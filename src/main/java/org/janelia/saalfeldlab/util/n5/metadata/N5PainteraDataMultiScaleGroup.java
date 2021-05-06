package org.janelia.saalfeldlab.util.n5.metadata;

public abstract class N5PainteraDataMultiScaleGroup extends N5GenericMultiScaleMetadata<PainteraSourceMetadata> {

  public N5PainteraDataMultiScaleGroup(PainteraSourceMetadata[] childrenMetadata, String basePath) {

	super(childrenMetadata, basePath);
  }

  public abstract PainteraMultiscaleGroup<? extends PainteraSourceMetadata> getDataGroupMetadata();
}
