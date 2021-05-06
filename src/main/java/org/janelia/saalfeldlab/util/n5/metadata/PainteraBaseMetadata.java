package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;

public interface PainteraBaseMetadata extends PhysicalMetadata {

  /**
   * @return true if label multi-scale group or single-scale; otherwise false
   */
  default boolean isLabel() {

	switch (getDataType()) {
	case UINT64:
	case UINT32:
	case INT64:
	  return true;
	default:
	  return false;
	}
  }

  default boolean isLabelMultiset() {

	return false;
  }

  /**
   * @return the value of the highest label ID if a label dataset or group; Otherwise null
   */
  default Long maxId() {

	return null;
  }

  default double[] getDownsamplingFactors(int scaleIdx) {

	return new double[]{1.0, 1.0, 1.0};
  }

  /**
   * @return the DataType of the data
   */
  DataType getDataType();

  /**
   * @return length 3 double[] specifying the resolution along each of the [x,y,z] axes of the data
   */
  default double[] getResolution() {

	return new double[]{1.0, 1.0, 1.0};
  }

  DatasetAttributes getAttributes();

  /**
   * @return length 3 double[] specifying the offset for each of the [x,y,z] axes of the data
   */
  default double[] getOffset() {

	return new double[]{0.0, 0.0, 0.0};
  }

  /**
   * @return the minimum intensity value of the data
   */
  default double min() {

	return 0.0;
  }

  /**
   * @return the maximum intensity value of the data
   */
  default double max() {

	return maxForDataType(getDataType());
  }

  /**
   * @return length 3 double[] specifying the units of each of the [x,y,z] axes of the data
   */
  @Override default String[] units() {

	return new String[]{"pixel", "pixel", "pixel"};
  }

  default AffineTransform3D getTransform() {

	return physicalTransform3d();
  }

  @Override default AffineGet physicalTransform() {

	final AffineTransform3D transform = new AffineTransform3D();
	final var resolution = getResolution();
	final var offset = getOffset();
	transform.set(
			resolution[0], 0, 0, offset[0],
			0, resolution[1], 0, offset[1],
			0, 0, resolution[2], offset[2]
	);
	return (AffineGet)transform;
  }

  static double maxForDataType(DataType dataType) {

	switch (dataType) {
	case UINT8:
	  return 0xff;
	case UINT16:
	  return 0xffff;
	case UINT32:
	  return 0xffffffffL;
	case UINT64:
	  return 2.0 * Long.MAX_VALUE;
	case INT8:
	  return Byte.MAX_VALUE;
	case INT16:
	  return Short.MAX_VALUE;
	case INT32:
	  return Integer.MAX_VALUE;
	case INT64:
	  return Long.MAX_VALUE;
	case FLOAT32:
	case FLOAT64:
	case OBJECT:
	default:
	  return 1.0;
	}
  }
}
