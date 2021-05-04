package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;

import java.util.Arrays;
import java.util.Comparator;

public interface PainteraMultiscaleGroup<T extends N5DatasetMetadata> extends N5MetadataGroup<T>, PainteraBaseMetadata{

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
   * @return the DatasetAttributes of the s0 dataset
   */
  @Override default DatasetAttributes getAttributes() {

	return getChildrenMetadata()[0].getAttributes();
  }

  /**
   * @return true if group is label, multiscale, and multiset; otherwise false
   */
  default boolean isLabelMultisetType() {

	return false;
  }
}
