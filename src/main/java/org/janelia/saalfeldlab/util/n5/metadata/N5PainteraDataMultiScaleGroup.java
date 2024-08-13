package org.janelia.saalfeldlab.util.n5.metadata;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.n5.universe.metadata.N5SpatialDatasetMetadata;
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMultiscaleMetadata;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class N5PainteraDataMultiScaleGroup extends SpatialMultiscaleMetadata<N5SpatialDatasetMetadata> {

	public static final Predicate<String> SCALE_LEVEL_PREDICATE = Pattern.compile("^s\\d+$").asPredicate();
	protected final N5PainteraDataMultiScaleMetadata dataGroup;

	public N5PainteraDataMultiScaleGroup(String basePath, final N5PainteraDataMultiScaleMetadata dataGroup) {

		super(basePath, dataGroup.getChildrenMetadata());
		this.dataGroup = dataGroup;
	}

	public N5PainteraDataMultiScaleMetadata getDataGroupMetadata() {

		return dataGroup;
	}

	@Override public AffineGet spatialTransform() {

		return dataGroup.spatialTransform();
	}

	@Override public String unit() {

		return dataGroup.unit();
	}

	@Override public AffineTransform3D spatialTransform3d() {

		return dataGroup.spatialTransform3d();
	}

	@Override
	public AffineGet[] spatialTransforms() {
		return dataGroup.spatialTransforms3d();
	}

	@Override
	public AffineTransform3D[] spatialTransforms3d() {
		return dataGroup.spatialTransforms3d();
	}
}
