package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.Arrays;
import java.util.Comparator;

public interface PainteraMultiscaleGroup<T extends N5DatasetMetadata & PainteraBaseMetadata> extends N5MetadataGroup<T>, PainteraBaseMetadata {

  /**
   * @return an array of the datasets in this group, sorted in order of s0..sN
   */
  default String[] sortedScaleDatasets() {

	final var pathsCopy = Arrays.copyOf(getPaths(), getPaths().length);
	Arrays.sort(pathsCopy, Comparator.comparingInt(s -> Integer.parseInt(s.replaceAll("[^\\d]", ""))));
	return pathsCopy;
  }

  /**
   * @return the datatype of the s0 dataset
   */
  @Override default DataType getDataType() {

	return getAttributes().getDataType();
  }

  /**
   * This assumes the results of getChildrenMetadata are sorted with respect to scale level.
   *
   * @return the DatasetAttributes of the s0 dataset
   */
  @Override default DatasetAttributes getAttributes() {

	return getChildrenMetadata()[0].getAttributes();
  }

  @Override default double[] getResolution() {

	return getChildrenMetadata()[0].getResolution();
  }

  @Override default double[] getOffset() {

	return getChildrenMetadata()[0].getOffset();
  }

  @Override default boolean isLabel() {

	return getChildrenMetadata()[0].isLabel();
  }

  @Override default boolean isLabelMultiset() {

	return getChildrenMetadata()[0].isLabelMultiset();
  }

  @Override default Long maxId() {

	return getChildrenMetadata()[0].maxId();
  }

  default double[] getDownsamplingFactors(int scaleIdx) {

	return getChildrenMetadata()[scaleIdx].getDownsamplingFactors();
  }

  @Override default double[] getDownsamplingFactors() {

	return getDownsamplingFactors(0);
  }

  @Override default double min() {

	return getChildrenMetadata()[0].min();
  }

  @Override default double max() {

	return getChildrenMetadata()[0].max();
  }

  @Override default String[] units() {

	return getChildrenMetadata()[0].units();
  }

  @Override default AffineTransform3D getTransform() {

	return getChildrenMetadata()[0].getTransform();
  }

  /**
   * @return true if group is label, multiscale, and multiset; otherwise false
   */
  default boolean isLabelMultisetType() {

	return false;
  }
}
