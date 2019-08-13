package org.janelia.saalfeldlab.paintera.config;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

public class Viewer3DConfig
{
	public static final int RENDERER_BLOCK_SIZE_MIN_VALUE = 16;

	public static final int RENDERER_BLOCK_SIZE_MAX_VALUE = 256;

	public static final int RENDERER_BLOCK_SIZE_DEFAULT_VALUE = 64;

	// TODO the Viewer3DFX and handler should probably hold an instance of this

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SimpleBooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);

	private final SimpleObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.BLACK);

	private final Affine affine = new Affine();

	// TODO this is only necessary while projects without serialized transform exist
	private boolean wasAffineSet = false;

	private final SimpleIntegerProperty rendererBlockSize = new SimpleIntegerProperty(RENDERER_BLOCK_SIZE_DEFAULT_VALUE);

	public BooleanProperty areMeshesEnabledProperty()
	{
		return this.areMeshesEnabled;
	}

	public BooleanProperty showBlockBoundariesProperty()
	{
		return this.showBlockBoundaries;
	}

	public IntegerProperty rendererBlockSizeProperty()
	{
		return this.rendererBlockSize;
	}

	public SimpleObjectProperty<Color> backgroundColorProperty() {
		return backgroundColor;
	}

	public void bindViewerToConfig(final Viewer3DFX viewer)
	{
		viewer.isMeshesEnabledProperty().bind(this.areMeshesEnabled);
		viewer.showBlockBoundariesProperty().bind(this.showBlockBoundaries);
		viewer.rendererBlockSizeProperty().bind(this.rendererBlockSize);

		final Affine affineCopy = this.affine.clone();
		final boolean wasAffineSet = this.wasAffineSet;
		viewer.addAffineListener(this::setAffine);
		viewer.backgroundFillProperty().bindBidirectional(this.backgroundColor);
		if (wasAffineSet) {
			LOG.debug("Setting viewer affine to {}", affineCopy);
			viewer.setAffine(affineCopy);
		}
	}

	public boolean isWasAffineSet() {
		return wasAffineSet;
	}

	public void set(final Viewer3DConfig that)
	{
		this.areMeshesEnabled.set(that.areMeshesEnabled.get());
		this.showBlockBoundaries.set(that.showBlockBoundaries.get());
		this.rendererBlockSize.set(that.rendererBlockSize.get());
		this.backgroundColor.set(that.backgroundColor.get());
		if (that.wasAffineSet)
			setAffine(that.affine);
	}

	public void setAffine(final Affine affine) {
		LOG.debug("Set affine {} to {}", this.affine, affine);
		this.affine.setToTransform(affine);
		this.wasAffineSet = true;
	}

	public Affine getAffineCopy() {
		return this.affine.clone();
	}

}
