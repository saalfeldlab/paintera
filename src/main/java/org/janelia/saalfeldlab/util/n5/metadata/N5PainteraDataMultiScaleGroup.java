package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;

public abstract class N5PainteraDataMultiScaleGroup extends N5MultiScaleMetadata {

  protected final N5MultiScaleMetadata dataGroup;

  public N5PainteraDataMultiScaleGroup(String basePath, final N5MultiScaleMetadata dataGroup) {

	super(basePath, dataGroup.getChildrenMetadata());
	this.dataGroup = dataGroup;
  }

  public N5MultiScaleMetadata getDataGroupMetadata() {

	return dataGroup;
  }

}
