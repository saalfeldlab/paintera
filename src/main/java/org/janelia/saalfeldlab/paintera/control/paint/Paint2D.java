package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.IntStream;

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

		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		final AffineTransform3D viewerTransformWithoutTranslation        = globalToViewerTransform.copy();
		labelToGlobalTransformWithoutTranslation.setTranslation(0.0, 0.0, 0.0);
		viewerTransformWithoutTranslation.setTranslation(0.0, 0.0, 0.0);

		// get maximum extent of pixels along z
		final double[] projections = PaintUtils.maximumVoxelDiagonalLengthPerDimension(
				labelToGlobalTransformWithoutTranslation,
				globalToViewerTransform
		                                                                              );

		final double factor = 0.5;
		final double xRange = factor * projections[0];
		final double yRange = factor * projections[1];
		final double zRange = (factor + brushDepth - 1) * projections[2];
		LOG.debug("range is {}", zRange);

		final double viewerRadius = Affine3DHelpers.extractScale(globalToViewerTransform, 0) * radius;
		final int viewerAxisInLabelCoordinates = PaintUtils.labelAxisCorrespondingToViewerAxis(
				labelToGlobalTransform,
				globalToViewerTransform,
				2
		                                                                                      );
		LOG.debug("Got coresspanding viewer axis in label coordinate system: {}", viewerAxisInLabelCoordinates);

		if (viewerAxisInLabelCoordinates < 0)
		{
			final double radiusX   = xRange + viewerRadius;
			final double radiusY   = yRange + viewerRadius;
			final double[] fillMin = {x - viewerRadius, y - viewerRadius, -zRange};
			final double[] fillMax = {x + viewerRadius, y + viewerRadius, +zRange};
			labelToViewerTransform.applyInverse(fillMin, fillMin);
			labelToViewerTransform.applyInverse(fillMax, fillMax);
			final double[] transformedFillMin = new double[3];
			final double[] transformedFillMax = new double[3];
			Arrays.setAll(transformedFillMin, d -> (long) Math.floor(Math.min(fillMin[d], fillMax[d])));
			Arrays.setAll(transformedFillMax, d -> (long) Math.ceil(Math.max(fillMin[d], fillMax[d])));

			// containingInterval might be too small
			final Interval conatiningInterval = Intervals.smallestContainingInterval(new FinalRealInterval(
					transformedFillMin,
					transformedFillMax
			));
			final AccessBoxRandomAccessibleOnGet<UnsignedLongType> accessTracker = labels instanceof
					                                                                       AccessBoxRandomAccessibleOnGet<?>
			                                                                       ?
			                                                                       (AccessBoxRandomAccessibleOnGet<UnsignedLongType>) labels
			                                                                       : new
					                                                                       AccessBoxRandomAccessibleOnGet<>(
					                                                                       labels);
			accessTracker.initAccessBox();
			final RandomAccess<UnsignedLongType> access = accessTracker.randomAccess(conatiningInterval);
			final RealPoint                      seed   = new RealPoint(x, y, 0.0);

			FloodFillTransformedCylinder3D.fill(
					labelToViewerTransform,
					radiusX,
					radiusY,
					zRange,
					access,
					seed,
					fillLabel
			                                   );

			final FinalInterval trackedInterval = new FinalInterval(accessTracker.getMin(), accessTracker.getMax());
			return trackedInterval;
		}
		else
		{
			LOG.debug("Painting axis aligned (optimized)");
			final double[] transformedRadius = new double[3];

			final int correspondingToXAxis = PaintUtils.labelAxisCorrespondingToViewerAxis(
					labelToGlobalTransform,
					globalToViewerTransform,
					0
			                                                                              );
			final int correspondingToYAxis = PaintUtils.labelAxisCorrespondingToViewerAxis(
					labelToGlobalTransform,
					globalToViewerTransform,
					1
			                                                                              );

			LOG.debug(
					"Corresponding axes in label coordinate system: x={} y={} z={}",
					correspondingToXAxis,
					correspondingToYAxis,
					viewerAxisInLabelCoordinates
			         );

			final double[] transformedXRadius = PaintUtils.viewerAxisInLabelCoordinates(
					labelToGlobalTransform,
					globalToViewerTransform,
					0,
					viewerRadius
			                                                                           );
			final double[] transformedYRadius = PaintUtils.viewerAxisInLabelCoordinates(
					labelToGlobalTransform,
					globalToViewerTransform,
					1,
					viewerRadius
			                                                                           );
			LOG.debug("Transformed radii: x={} y={}", transformedXRadius, transformedYRadius);

			transformedRadius[correspondingToXAxis] = transformedXRadius[correspondingToXAxis];
			transformedRadius[correspondingToYAxis] = transformedYRadius[correspondingToYAxis];

			final double[] seed = new double[] {x, y, 0.0};
			labelToViewerTransform.applyInverse(seed, seed);

			final long slicePos = Math.round(seed[viewerAxisInLabelCoordinates]);

			final long[] center = {
					Math.round(seed[viewerAxisInLabelCoordinates == 0 ? 1 : 0]),
					Math.round(seed[viewerAxisInLabelCoordinates != 2 ? 2 : 1])
			};

			final long[] sliceRadii = {
					(long) Math.ceil(transformedRadius[viewerAxisInLabelCoordinates == 0 ? 1 : 0]),
					(long) Math.ceil(transformedRadius[viewerAxisInLabelCoordinates != 2 ? 2 : 1])
			};

			LOG.debug("Transformed radius={}", transformedRadius);

			final long numSlices = Math.max((long) Math.ceil(brushDepth) - 1, 0);

			for (long i = slicePos - numSlices; i <= slicePos + numSlices; ++i)
			{
				final MixedTransformView<UnsignedLongType> slice = Views.hyperSlice(
						labels,
						viewerAxisInLabelCoordinates,
						i
				                                                                   );

				final Neighborhood<UnsignedLongType> neighborhood;
				if (sliceRadii[0] == sliceRadii[1])
				{
					neighborhood = HyperSphereNeighborhood
							.<UnsignedLongType>factory()
							.create(
									center,
									sliceRadii[0],
									slice.randomAccess()
							       );
				}
				else
				{
					neighborhood = HyperEllipsoidNeighborhood
							.<UnsignedLongType>factory()
							.create(
									center,
									sliceRadii,
									slice.randomAccess()
							       );
				}

				LOG.debug("Painting with radii {} centered at {} in slice {}", sliceRadii, center, slicePos);

				for (final UnsignedLongType t : neighborhood)
				{
					t.set(fillLabel);
				}
			}

			final long[] min2D = IntStream.range(0, 2).mapToLong(d -> center[d] - sliceRadii[d]).toArray();
			final long[] max2D = IntStream.range(0, 2).mapToLong(d -> center[d] + sliceRadii[d]).toArray();

			final long[] min = {
					viewerAxisInLabelCoordinates == 0 ? slicePos - numSlices : min2D[0],
					viewerAxisInLabelCoordinates == 1
					? slicePos - numSlices
					: viewerAxisInLabelCoordinates == 0 ? min2D[0] : min2D[1],
					viewerAxisInLabelCoordinates == 2 ? slicePos - numSlices : min2D[1]
			};

			final long[] max = {
					viewerAxisInLabelCoordinates == 0 ? slicePos + numSlices : max2D[0],
					viewerAxisInLabelCoordinates == 1
					? slicePos + numSlices
					: viewerAxisInLabelCoordinates == 0 ? max2D[0] : max2D[1],
					viewerAxisInLabelCoordinates == 2 ? slicePos + numSlices : max2D[1]
			};

			return new FinalInterval(min, max);

		}
	}

}
