package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineTransform3D;
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

  public double[] groupPixelResolution() {

	final AffineTransform3D transform = dataGroup.spatialTransform3d();
	final var x = transform.get(0, 0);
	final var y = transform.get(1, 1);
	final var z = transform.get(2, 2);
	return new double[]{x, y, z};
  }

  public double[] groupOffset() {

	final AffineTransform3D transform = dataGroup.spatialTransform3d();
	final var translation = transform.getTranslation();
	final var x = translation[0];
	final var y = translation[1];
	final var z = translation[2];
	return new double[]{x, y, z};
  }

}
