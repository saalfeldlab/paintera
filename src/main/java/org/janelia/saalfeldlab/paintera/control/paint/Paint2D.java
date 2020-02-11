package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.IntStream;

import bdv.util.Affine3DHelpers;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.logic.NativeBoolType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tmp.net.imglib2.algorithm.neighborhood.HyperEllipsoidNeighborhood;

public class Paint2D
{

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static Interval paint(
			final RandomAccessible<UnsignedLongType> labels,
			final long fillLabel,
			final double x,
			final double y,
			final double radius,
			final double brushDepth,
			final AffineTransform3D labelToViewerTransform,
			final AffineTransform3D globalToViewerTransform,
			final AffineTransform3D labelToGlobalTransform)
	{

		// TODO what about brush depth?

		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		final AffineTransform3D viewerTransformWithoutTranslation        = globalToViewerTransform.copy();
		labelToGlobalTransformWithoutTranslation.setTranslation(0.0, 0.0, 0.0);
		viewerTransformWithoutTranslation.setTranslation(0.0, 0.0, 0.0);
		final double[] globalRadiusAlongGlobalX = { radius, 0.0, 0.0 };
		viewerTransformWithoutTranslation.apply(globalRadiusAlongGlobalX, globalRadiusAlongGlobalX);

		final double[] zAxis = new double[] {0.0, 0.0, 1.0};
		viewerTransformWithoutTranslation.applyInverse(zAxis, zAxis);
		labelToGlobalTransformWithoutTranslation.applyInverse(zAxis, zAxis);
		final double length = LinAlgHelpers.length(zAxis);
		Arrays.setAll(zAxis, d -> zAxis[d] / length);
		labelToGlobalTransformWithoutTranslation.apply(zAxis, zAxis);
		viewerTransformWithoutTranslation.apply(zAxis, zAxis);
		final double[] voxelLengths = PaintUtils.maximumVoxelDiagonalLengthPerDimension(labelToGlobalTransformWithoutTranslation, viewerTransformWithoutTranslation);
//		final double dz = zAxis[2];
		final double dz = voxelLengths[2] / 2.0;

		// get maximum extent of pixels along z
		final double viewerRadius = LinAlgHelpers.length(globalRadiusAlongGlobalX);
		final double viewerRadiusSquared = viewerRadius * viewerRadius;
		LOG.info("x={} y={} viewerRadius={}", x, y, viewerRadius);

		final FunctionRealRandomAccessible<BoolType> mask = new FunctionRealRandomAccessible<>(
				3,
				() -> (pos, t) -> t.set(isWithinRadiusOf(x, y, dz, viewerRadiusSquared, pos)),
				BoolType::new);
		final AffineRandomAccessible<BoolType, AffineGet> maskInLabelSpace = RealViews.affine(mask, labelToViewerTransform.inverse());
		final double[] boundingBoxMin = new double[]{x - viewerRadius, y - viewerRadius, -dz};
		final double[] boundingBoxMax = new double[]{x + viewerRadius, y + viewerRadius, +dz};
		labelToViewerTransform.applyInverse(boundingBoxMin, boundingBoxMin);
		labelToViewerTransform.applyInverse(boundingBoxMax, boundingBoxMax);
		final Interval boundingBox = Intervals.smallestContainingInterval(FinalRealInterval.createMinMax(
				Math.ceil(Math.min(boundingBoxMin[0], boundingBoxMax[0])),
				Math.ceil(Math.min(boundingBoxMin[1], boundingBoxMax[1])),
				Math.ceil(Math.min(boundingBoxMin[2], boundingBoxMax[2])),
				Math.floor(Math.max(boundingBoxMin[0], boundingBoxMax[0])),
				Math.floor(Math.max(boundingBoxMin[1], boundingBoxMax[1])),
				Math.floor(Math.max(boundingBoxMin[2], boundingBoxMax[2]))));
		LOG.info("Bounding box={}", boundingBox);
		final IntervalView<NativeBoolType> tempFill = Views.translate(ArrayImgs.booleans(Intervals.dimensionsAsLongArray(boundingBox)), Intervals.minAsLongArray(boundingBox));
		tempFill.forEach(NativeBoolType::setZero);

		final double[] seed = new double[] {x, y, 0.0};
		final double[] seedInLabelSpace = new double[seed.length];
		labelToViewerTransform.inverse().apply(seed, seedInLabelSpace);
		final long[] seedRounded = new long[seed.length];
		Arrays.setAll(seedRounded, d -> Math.round(seedInLabelSpace[d]));
		final double[] seedBack = new double[seedRounded.length];
		Arrays.setAll(seedBack, d -> seedRounded[d]);
		labelToViewerTransform.apply(seedBack, seedBack);
		labelToViewerTransform.apply(seedInLabelSpace, seedInLabelSpace);


		final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = labels instanceof AccessBoxRandomAccessibleOnGet<?>
				? (AccessBoxRandomAccessibleOnGet<UnsignedLongType>) labels
				: new AccessBoxRandomAccessibleOnGet<>(labels);
		accessTracker.initAccessBox();

		LoopBuilder
				.setImages(Views.interval(maskInLabelSpace, boundingBox), Views.interval(accessTracker, boundingBox))
				.forEachPixel((s, t) -> {
					if (s.get())
						t.set(fillLabel);
		});

		return new FinalInterval(accessTracker.getMin(), accessTracker.getMax());

	}

	private static boolean isWithinRadiusOf(final double x, final double y, final double dz, final double radiusSquared, final RealLocalizable pos) {
		if (Math.abs(pos.getDoublePosition(2)) <= dz) {
			final double dx = pos.getDoublePosition(0) - x;
			final double dy = pos.getDoublePosition(1) - y;
			return dx*dx + dy*dy <= radiusSquared;
		}
		return false;
	}

}
