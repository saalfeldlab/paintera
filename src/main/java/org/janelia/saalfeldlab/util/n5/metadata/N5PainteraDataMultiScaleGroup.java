package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.metadata.N5MultiScaleMetadata;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class N5PainteraDataMultiScaleGroup extends N5MultiScaleMetadata {

  public static final Predicate<String> SCALE_LEVEL_PREDICATE = Pattern.compile("^s\\d+$").asPredicate();
  protected final N5PainteraDataMultiScaleMetadata dataGroup;

  public N5PainteraDataMultiScaleGroup(String basePath, final N5PainteraDataMultiScaleMetadata dataGroup) {

	super(basePath, dataGroup.getChildrenMetadata());
	this.dataGroup = dataGroup;
  }

  public N5PainteraDataMultiScaleMetadata getDataGroupMetadata() {

	return dataGroup;
  }

}
