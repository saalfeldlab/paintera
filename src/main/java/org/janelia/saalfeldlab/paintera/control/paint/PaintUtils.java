package org.janelia.saalfeldlab.paintera.control.paint;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class PaintUtils {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static int labelAxisCorrespondingToViewerAxis(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform,
			final int axis) {

		return labelAxisCorrespondingToViewerAxis(labelToGlobalTransform, viewerTransform, axis, 0.0);
	}

	/**
	 * @param labelToGlobalTransform
	 * @param viewerTransform
	 * @param axis                   {@code 0=x, 1=y, 2=z}
	 * @param tolerance              {@code 0 <= tolerance << 1}
	 * @return {@code -1} if axis no unique corresponding axis according to tolerance, else {@code 0=x, 1=y, 2=z}
	 */
	public static int labelAxisCorrespondingToViewerAxis(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform,
			final int axis,
			final double tolerance) {

		final double[] transformedAxis = viewerAxisInLabelCoordinates(labelToGlobalTransform, viewerTransform, axis);
		LinAlgHelpers.normalize(transformedAxis);
		if (Math.abs(Math.abs(transformedAxis[0]) - 1.0) <= tolerance) {
			return 0;
		}
		if (Math.abs(Math.abs(transformedAxis[1]) - 1.0) <= tolerance) {
			return 1;
		}
		if (Math.abs(Math.abs(transformedAxis[2]) - 1.0) <= tolerance) {
			return 2;
		}
		return -1;
	}

	public static double[] viewerAxisInLabelCoordinates(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform,
			final int axis) {

		return viewerAxisInLabelCoordinates(labelToGlobalTransform, viewerTransform, axis, 1.0);
	}

	/**
	 * @param labelToGlobalTransform
	 * @param viewerTransform
	 * @param axis                   {@code 0=x, 1=y, 2=z}
	 * @param length                 {@code != 0}
	 * @return
	 */
	public static double[] viewerAxisInLabelCoordinates(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform,
			final int axis,
			final double length) {

		final AffineTransform3D labelToGlobalTransformWithoutTranslation = duplicateWithoutTranslation(labelToGlobalTransform);
		final AffineTransform3D viewerTransformWithoutTranslation = duplicateWithoutTranslation(viewerTransform);
		final AffineTransform3D labelToViewerTransformWithoutTranslation = labelToGlobalTransformWithoutTranslation
				.preConcatenate(viewerTransformWithoutTranslation);

		final double[] viewerUnitAxis = {0.0, 0.0, 0.0};
		viewerUnitAxis[axis] = length;

		labelToViewerTransformWithoutTranslation.applyInverse(viewerUnitAxis, viewerUnitAxis);
		return viewerUnitAxis;
	}

	/**
	 * This should be equivalent to {@link #maximumVoxelDiagonalLengthPerDimension(AffineTransform3D,
	 * AffineTransform3D)}.
	 *
	 * @param labelToGlobalTransform
	 * @param viewerTransform
	 * @return
	 */
	public static double[] labelUnitLengthAlongViewerAxis(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform) {

		final AffineTransform3D labelToGlobalTransformWithoutTranslation = duplicateWithoutTranslation(labelToGlobalTransform);
		final AffineTransform3D viewerTransformWithoutTranslation = duplicateWithoutTranslation(viewerTransform);
		final AffineTransform3D labelToViewerTransformWithoutTranslation = labelToGlobalTransformWithoutTranslation
				.preConcatenate(viewerTransformWithoutTranslation);

		final double[] unitX = {1.0, 0.0, 0.0};
		final double[] unitY = {0.0, 1.0, 0.0};
		final double[] unitZ = {0.0, 0.0, 1.0};
		labelToViewerTransformWithoutTranslation.applyInverse(unitX, unitX);
		labelToViewerTransformWithoutTranslation.applyInverse(unitY, unitY);
		labelToViewerTransformWithoutTranslation.applyInverse(unitZ, unitZ);

		LinAlgHelpers.normalize(unitX);
		LinAlgHelpers.normalize(unitY);
		LinAlgHelpers.normalize(unitZ);

		labelToViewerTransformWithoutTranslation.apply(unitX, unitX);
		labelToViewerTransformWithoutTranslation.apply(unitY, unitY);
		labelToViewerTransformWithoutTranslation.apply(unitZ, unitZ);

		return new double[]{
				unitX[0],
				unitY[1],
				unitZ[2]
		};

	}

	public static double[] maximumVoxelDiagonalLengthPerDimension(
			final AffineTransform3D labelToGlobalTransform,
			final AffineTransform3D viewerTransform) {

		final double[] unitX = {1.0, 0.0, 0.0};
		final double[] unitY = {0.0, 1.0, 0.0};
		final double[] unitZ = {0.0, 0.0, 1.0};
		final AffineTransform3D labelToGlobalTransformWithoutTranslation = duplicateWithoutTranslation(labelToGlobalTransform);
		final AffineTransform3D viewerTransformWithoutTranslation = duplicateWithoutTranslation(viewerTransform);
		labelToGlobalTransformWithoutTranslation.apply(unitX, unitX);
		labelToGlobalTransformWithoutTranslation.apply(unitY, unitY);
		labelToGlobalTransformWithoutTranslation.apply(unitZ, unitZ);
		viewerTransformWithoutTranslation.apply(unitX, unitX);
		viewerTransformWithoutTranslation.apply(unitY, unitY);
		viewerTransformWithoutTranslation.apply(unitZ, unitZ);
		LOG.debug("Transformed unit vectors x={} y={} z={}", unitX, unitY, unitZ);
		final double[] projections = new double[]{
				length(unitX[0], unitY[0], unitZ[0]),
				length(unitX[1], unitY[1], unitZ[1]),
				length(unitX[2], unitY[2], unitZ[2])
		};
		LOG.debug("Projections={}", projections);
		return projections;
	}

	public static AffineTransform3D duplicateWithoutTranslation(final AffineTransform3D transform) {

		final AffineTransform3D duplicate = transform.copy();
		removeTranslation(duplicate);
		return duplicate;
	}

	public static void removeTranslation(final AffineTransform3D transform) {

		transform.setTranslation(0.0, 0.0, 0.0);
	}

	private static double length(double... vec) {

		return LinAlgHelpers.length(vec);
	}

}
