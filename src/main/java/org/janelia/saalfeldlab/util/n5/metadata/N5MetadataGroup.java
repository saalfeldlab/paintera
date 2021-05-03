package org.janelia.saalfeldlab.util.n5.metadata;

public interface N5MetadataGroup<T extends N5Metadata> extends N5Metadata {

  String[] getPaths();

  T[] getChildrenMetadata();
}
