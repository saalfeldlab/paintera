package org.janelia.saalfeldlab.paintera.control.paint;

import bdv.util.Affine3DHelpers;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.stream.IntStream;

public class Paint2D {

  private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static Interval paintIntoTransformedSource(
		  final RandomAccessible<UnsignedLongType> labelsInSource,
		  final long fillLabel,
		  final int orthoAxis,
		  final double x,
		  final double y,
		  final double radius,
		  final double brushDepth,
		  final AffineTransform3D labelSourceToViewerTransform,
		  final AffineTransform3D globalToViewerTransform,
		  final AffineTransform3D labelSourceToGlobalTransform) {

	final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelSourceToGlobalTransform.copy();
	labelToGlobalTransformWithoutTranslation.setTranslation(0.0, 0.0, 0.0);

	// get maximum extent of pixels along z
	final double[] projections = PaintUtils.maximumVoxelDiagonalLengthPerDimension(
			labelToGlobalTransformWithoutTranslation,
			globalToViewerTransform
	);

	final double factor = Math.sqrt(0.5);
	final double xRange = factor * projections[0];
	final double yRange = factor * projections[1];
	final double zRange = (factor + brushDepth - 1) * projections[2];
	LOG.debug("range is {}", zRange);

	final double viewerRadius = Affine3DHelpers.extractScale(globalToViewerTransform, 0) * radius;
	final int viewerAxisInLabelCoordinates = PaintUtils.labelAxisCorrespondingToViewerAxis(
			labelSourceToGlobalTransform,
			globalToViewerTransform,
			orthoAxis
	);
	LOG.debug("Got coresspanding viewer axis in label coordinate system: {}", viewerAxisInLabelCoordinates);

	// paint efficiently only if axis-aligned and isotropic within plane
	if (viewerAxisInLabelCoordinates >= 0 && viewerAxisInLabelCoordinates < 3) {
	  LOG.debug("Painting axis aligned (optimized)");
	  final double[] transformedRadius = new double[3];

	  final int correspondingToXAxis = PaintUtils.labelAxisCorrespondingToViewerAxis(
			  labelSourceToGlobalTransform,
			  globalToViewerTransform,
			  0);
	  final int correspondingToYAxis = PaintUtils.labelAxisCorrespondingToViewerAxis(
			  labelSourceToGlobalTransform,
			  globalToViewerTransform,
			  1);

	  LOG.debug(
			  "Corresponding axes in label coordinate system: x={} y={} z={}",
			  correspondingToXAxis,
			  correspondingToYAxis,
			  viewerAxisInLabelCoordinates);

	  final double[] transformedXRadius = PaintUtils.viewerAxisInLabelCoordinates(
			  labelSourceToGlobalTransform,
			  globalToViewerTransform,
			  0,
			  viewerRadius);
	  final double[] transformedYRadius = PaintUtils.viewerAxisInLabelCoordinates(
			  labelSourceToGlobalTransform,
			  globalToViewerTransform,
			  1,
			  viewerRadius);
	  LOG.debug("Transformed radii: x={} y={}", transformedXRadius, transformedYRadius);

	  transformedRadius[correspondingToXAxis] = transformedXRadius[correspondingToXAxis];
	  transformedRadius[correspondingToYAxis] = transformedYRadius[correspondingToYAxis];

	  final double[] seed = new double[]{x, y, 0.0};
	  labelSourceToViewerTransform.applyInverse(seed, seed);

	  final long slicePos = Math.round(seed[viewerAxisInLabelCoordinates]);

	  final long[] center = {
			  Math.round(seed[viewerAxisInLabelCoordinates == 0 ? 1 : 0]),
			  Math.round(seed[viewerAxisInLabelCoordinates != 2 ? 2 : 1])
	  };
	  final long[] sliceRadii = {
			  Math.round(transformedRadius[viewerAxisInLabelCoordinates == 0 ? 1 : 0]),
			  Math.round(transformedRadius[viewerAxisInLabelCoordinates != 2 ? 2 : 1])
	  };

	  // only do this if we can actually paint a sphere, i.e. data is isotropic within plane, no .
	  if (sliceRadii[0] == sliceRadii[1]) {

		LOG.debug("Transformed radius={}", transformedRadius);

		final long numSlices = Math.max((long)Math.ceil(brushDepth) - 1, 0);

		for (long i = slicePos - numSlices; i <= slicePos + numSlices; ++i) {
		  final MixedTransformView<UnsignedLongType> slice = Views.hyperSlice(
				  labelsInSource,
				  viewerAxisInLabelCoordinates,
				  i);

		  final Neighborhood<UnsignedLongType> neighborhood = HyperSphereNeighborhood
				  .<UnsignedLongType>factory()
				  .create(
						  center,
						  sliceRadii[0],
						  slice.randomAccess());

		  LOG.debug("Painting with radii {} centered at {} in slice {}", sliceRadii, center, slicePos);

		  for (final UnsignedLongType t : neighborhood) {
			t.set(fillLabel);
		  }
		}

		final long[] min2D = IntStream.range(0, 2).mapToLong(d -> center[d] - sliceRadii[d]).toArray();
		final long[] max2D = IntStream.range(0, 2).mapToLong(d -> center[d] + sliceRadii[d]).toArray();

		final long[] min = {
				viewerAxisInLabelCoordinates == 0 ? slicePos - numSlices : min2D[0],
				viewerAxisInLabelCoordinates == 1 ? slicePos - numSlices : viewerAxisInLabelCoordinates == 0 ? min2D[0] : min2D[1],
				viewerAxisInLabelCoordinates == 2 ? slicePos - numSlices : min2D[1]
		};

		final long[] max = {
				viewerAxisInLabelCoordinates == 0 ? slicePos + numSlices : max2D[0],
				viewerAxisInLabelCoordinates == 1 ? slicePos + numSlices : viewerAxisInLabelCoordinates == 0 ? max2D[0] : max2D[1],
				viewerAxisInLabelCoordinates == 2 ? slicePos + numSlices : max2D[1]
		};

		return new FinalInterval(min, max);
	  }

	}
	final double radiusX = xRange + viewerRadius;
	final double radiusY = yRange + viewerRadius;
	final double[] fillMin = {x - radiusX, y - radiusY, -zRange};
	final double[] fillMax = {x + radiusX, y + radiusY, +zRange};

	final Interval containingInterval = Intervals
			.smallestContainingInterval(labelSourceToViewerTransform.inverse().estimateBounds(new FinalRealInterval(fillMin, fillMax)));
	final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = labelsInSource instanceof AccessBoxRandomAccessibleOnGet<?>
			? (AccessBoxRandomAccessibleOnGet<UnsignedLongType>)labelsInSource
			: new AccessBoxRandomAccessibleOnGet<>(labelsInSource);
	accessTracker.initAccessBox();
	final RandomAccess<UnsignedLongType> access = accessTracker.randomAccess(containingInterval);
	final RealPoint seed = new RealPoint(x, y, 0.0);

	FloodFillTransformedCylinder3D.fill(
			labelSourceToViewerTransform,
			radiusX,
			radiusY,
			zRange,
			access,
			seed,
			fillLabel
	);

	return new FinalInterval(accessTracker.getMin(), accessTracker.getMax());
  }

  public static Interval paintIntoViewer(
		  final RandomAccessible<UnsignedLongType> labelsInViewer,
		  final long fillLabel,
		  final RealPoint seedPoint,
		  final double viewerRadius) {

	final long[] center = new long[]{Math.round(seedPoint.getDoublePosition(0)), Math.round(seedPoint.getDoublePosition(1))};
	final long[] sliceRadii = new long[]{Math.round(viewerRadius), Math.round(viewerRadius)};

	final Neighborhood<UnsignedLongType> neighborhood = HyperSphereNeighborhood
			.<UnsignedLongType>factory()
			.create(
					center,
					sliceRadii[0],
					labelsInViewer.randomAccess());

	LOG.debug("Painting with radii {} centered at {} in slice {}", sliceRadii, center, 0);

	for (final UnsignedLongType t : neighborhood) {
	  t.set(fillLabel);
	}

	final long[] min2D = IntStream.range(0, 2).mapToLong(d -> center[d] - sliceRadii[d]).toArray();
	final long[] max2D = IntStream.range(0, 2).mapToLong(d -> center[d] + sliceRadii[d]).toArray();

	final long[] min = {min2D[0], min2D[1], 0};
	final long[] max = {max2D[0], max2D[1], 0};

	return new FinalInterval(min, max);
  }

}
