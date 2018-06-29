package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.Affine3DHelpers;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.AccessBoxRandomAccessibleOnGet;
import net.imglib2.util.Intervals;

public class Paint2D
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static Interval paint(
			final RandomAccessible< UnsignedLongType > labels,
			final long fillLabel,
			final double x,
			final double y,
			final double radius,
			final double brushDepth,
			final AffineTransform3D labelToViewerTransform,
			final AffineTransform3D globalToViewerTransform,
			final AffineTransform3D labelToGlobalTransform )
	{

		final AffineTransform3D labelToGlobalTransformWithoutTranslation = labelToGlobalTransform.copy();
		final AffineTransform3D viewerTransformWithoutTranslation = globalToViewerTransform.copy();
		labelToGlobalTransformWithoutTranslation.setTranslation( 0.0, 0.0, 0.0 );
		viewerTransformWithoutTranslation.setTranslation( 0.0, 0.0, 0.0 );

		// get maximum extent of pixels along z
		final double[] projections = PaintUtils.maximumVoxelDiagonalLengthPerDimension( labelToGlobalTransformWithoutTranslation, globalToViewerTransform );

		final double factor = 0.5;
		final double xRange = factor * projections[ 0 ];
		final double yRange = factor * projections[ 1 ];
		final double zRange = ( factor + brushDepth - 1 ) * projections[ 2 ];
		LOG.debug( "range is {}", zRange );

		final double viewerRadius = Affine3DHelpers.extractScale( globalToViewerTransform, 0 ) * radius;
		final double radiusX = xRange + viewerRadius;
		final double radiusY = yRange + viewerRadius;
		final double[] fillMin = { x - viewerRadius, y - viewerRadius, -zRange };
		final double[] fillMax = { x + viewerRadius, y + viewerRadius, +zRange };
		labelToViewerTransform.applyInverse( fillMin, fillMin );
		labelToViewerTransform.applyInverse( fillMax, fillMax );
		final double[] transformedFillMin = new double[ 3 ];
		final double[] transformedFillMax = new double[ 3 ];
		Arrays.setAll( transformedFillMin, d -> ( long ) Math.floor( Math.min( fillMin[ d ], fillMax[ d ] ) ) );
		Arrays.setAll( transformedFillMax, d -> ( long ) Math.ceil( Math.max( fillMin[ d ], fillMax[ d ] ) ) );

		// containingInterval might be too small
		final Interval conatiningInterval = Intervals.smallestContainingInterval( new FinalRealInterval( transformedFillMin, transformedFillMax ) );
		final AccessBoxRandomAccessibleOnGet< UnsignedLongType > accessTracker = labels instanceof AccessBoxRandomAccessibleOnGet< ? >
				? ( AccessBoxRandomAccessibleOnGet< UnsignedLongType > ) labels
				: new AccessBoxRandomAccessibleOnGet<>( labels );
		accessTracker.initAccessBox();
		final RandomAccess< UnsignedLongType > access = accessTracker.randomAccess( conatiningInterval );
		final RealPoint seed = new RealPoint( x, y, 0.0 );

		FloodFillTransformedCylinder3D.fill( labelToViewerTransform, radiusX, radiusY, zRange, access, seed, fillLabel );

		final FinalInterval trackedInterval = new FinalInterval( accessTracker.getMin(), accessTracker.getMax() );
		return trackedInterval;
	}

}
