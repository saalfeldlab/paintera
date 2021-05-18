package org.janelia.saalfeldlab.util.n5.metadata;

public interface N5MetadataGroup<T extends N5Metadata> extends N5Metadata {

  /**
   * @return an array of the paths to each of the children in this group, with respect to the base of the container
   */
  String[] getPaths();

  /**
   * Note: This is NOT gauranteeed to be sorted. Sort with
   *
   * @return an array of the metadata for the children of this group
   */
  T[] getChildrenMetadata();
}
