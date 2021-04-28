package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class MultiscaleMetadata<T extends N5DatasetMetadata & PhysicalMetadata> implements PhysicalMetadata {

  static final Predicate<String> scaleLevelPredicate = Pattern.compile("^s\\d+$").asPredicate();
  final private String[] paths;

  final private AffineTransform3D[] transforms;

  final private String[] units;

  final private N5DatasetMetadata[] childrenMetadata;

  protected static final Predicate<String> SCALE_LEVEL_PREDICATE = Pattern.compile("^s\\d+$").asPredicate();

  protected MultiscaleMetadata() {

	childrenMetadata = null;
	paths = null;
	transforms = null;
	units = null;
  }

  public MultiscaleMetadata(final T[] childrenMetadata) {

	Objects.requireNonNull(childrenMetadata);
	this.childrenMetadata = childrenMetadata;

	final int N = childrenMetadata.length;
	transforms = new AffineTransform3D[N];
	paths = new String[N];
	units = childrenMetadata[0].units();

	int i = 0;
	for (T meta : childrenMetadata) {
	  Objects.requireNonNull(meta);
	  paths[i] = meta.getPath();
	  transforms[i] = meta.physicalTransform3d();
	}
  }

  public MultiscaleMetadata(final String[] paths, final AffineTransform3D[] transforms, final String[] units) {

	Objects.requireNonNull(paths);
	Objects.requireNonNull(transforms);

	for (final String path : paths)
	  Objects.requireNonNull(path);
	for (final AffineTransform3D transform : transforms)
	  Objects.requireNonNull(transform);

	this.paths = paths;
	this.transforms = transforms;
	this.units = units;
	this.childrenMetadata = null;
  }

  public String[] getPaths() {

	return paths;
  }

  public AffineTransform3D[] getTransforms() {

	return transforms;
  }

  public N5DatasetMetadata[] getChildrenMetadata() {

	return childrenMetadata;
  }

  @Override
  public AffineGet physicalTransform() {
	// by default, spatial transforms are specified by the individual scales by default
	return null;
  }

  @Override
  public String[] units() {

	return units;
  }

}
