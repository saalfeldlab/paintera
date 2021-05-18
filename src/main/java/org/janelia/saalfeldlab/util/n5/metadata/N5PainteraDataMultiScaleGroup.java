package org.janelia.saalfeldlab.util.n5.metadata;

public abstract class N5PainteraDataMultiScaleGroup extends N5GenericMultiScaleMetadata<PainteraSourceMetadata> {

  /**
   * @param childrenMetadata this should be the childrenMetadata of the data group
   * @param basePath         path of this group with relatvie to the root of the container
   */
  public N5PainteraDataMultiScaleGroup(PainteraSourceMetadata[] childrenMetadata, String basePath) {

	super(childrenMetadata, basePath);
  }

  public abstract PainteraMultiscaleGroup<? extends PainteraSourceMetadata> getDataGroupMetadata();

  @Override public double[] getResolution() {

	return getDataGroupMetadata().getResolution();
  }
}
