package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

public class PaintUtils
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 *
	 * This should be equivalent to
	 * {@link #maximumVoxelDiagonalLengthPerDimension(AffineTransform3D, AffineTransform3D)}.
	 *
	 * @param labelToGlobalTransform
	 * @param viewerTransform
	 * @return
	 */
	public static double[] labelUnitLengthAlongViewerAxis(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform )
	{
		final double[] unitX = { 1.0, 0.0, 0.0 };
		final double[] unitY = { 0.0, 1.0, 0.0 };
		final double[] unitZ = { 0.0, 0.0, 1.0 };
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = duplicateWithoutTranslation( labelToGlobalTransform );
		final AffineTransform3D viewerTransformWithoutTranslation = duplicateWithoutTranslation( viewerTransform );
		final AffineTransform3D labelToViewerTransformWithoutTranslation = labelToGlobalTransformWithoutTranslation.preConcatenate( viewerTransformWithoutTranslation );
		labelToViewerTransformWithoutTranslation.applyInverse( unitX, unitX );
		labelToViewerTransformWithoutTranslation.applyInverse( unitY, unitY );
		labelToViewerTransformWithoutTranslation.applyInverse( unitZ, unitZ );

		LinAlgHelpers.normalize( unitX );
		LinAlgHelpers.normalize( unitY );
		LinAlgHelpers.normalize( unitZ );

		labelToViewerTransformWithoutTranslation.apply( unitX, unitX );
		labelToViewerTransformWithoutTranslation.apply( unitY, unitY );
		labelToViewerTransformWithoutTranslation.apply( unitZ, unitZ );

		return new double[] {
				unitX[ 0 ],
				unitY[ 1 ],
				unitZ[ 2 ]
		};

	}

	public static double[] maximumVoxelDiagonalLengthPerDimension(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform )
	{
		final double[] unitX = { 1.0, 0.0, 0.0 };
		final double[] unitY = { 0.0, 1.0, 0.0 };
		final double[] unitZ = { 0.0, 0.0, 1.0 };
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = duplicateWithoutTranslation( labelToGlobalTransform );
		final AffineTransform3D viewerTransformWithoutTranslation = duplicateWithoutTranslation( viewerTransform );
		labelToGlobalTransformWithoutTranslation.apply( unitX, unitX );
		labelToGlobalTransformWithoutTranslation.apply( unitY, unitY );
		labelToGlobalTransformWithoutTranslation.apply( unitZ, unitZ );
		viewerTransformWithoutTranslation.apply( unitX, unitX );
		viewerTransformWithoutTranslation.apply( unitY, unitY );
		viewerTransformWithoutTranslation.apply( unitZ, unitZ );
		LOG.debug( "Transformed unit vectors x={} y={} z={}", unitX, unitY, unitZ );
		final double[] projections = new double[] {
				( Math.abs( unitX[ 0 ] ) + Math.abs( unitY[ 0 ] ) + Math.abs( unitZ[ 0 ] ) ),
				( Math.abs( unitX[ 1 ] ) + Math.abs( unitY[ 1 ] ) + Math.abs( unitZ[ 1 ] ) ),
				( Math.abs( unitX[ 2 ] ) + Math.abs( unitY[ 2 ] ) + Math.abs( unitZ[ 2 ] ) )
		};
		LOG.debug( "Projections={}", projections );
		return projections;
	}

	public static AffineTransform3D duplicateWithoutTranslation( final AffineTransform3D transform )
	{
		final AffineTransform3D duplicate = transform.copy();
		removeTranslation( duplicate );
		return duplicate;
	}

	public static void removeTranslation( final AffineTransform3D transform )
	{
		transform.setTranslation( 0.0, 0.0, 0.0 );
	}

}
