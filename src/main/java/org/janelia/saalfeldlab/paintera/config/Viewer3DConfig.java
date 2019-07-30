package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.transform.Affine;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class Viewer3DConfig
{

	// TODO the Viewer3DFX and handler should probably hold an instance of this

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SimpleBooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	private final Affine affine = new Affine();

	// TODO this is only necessary while projects without serialized transform exist
	private boolean wasAffineSet = false;

	public BooleanProperty areMeshesEnabledProperty()
	{
		return this.areMeshesEnabled;
	}

	public void bindViewerToConfig(final Viewer3DFX viewer)
	{
		viewer.isMeshesEnabledProperty().bind(this.areMeshesEnabled);
		final Affine affineCopy = this.affine.clone();
		final boolean wasAffineSet = this.wasAffineSet;
		viewer.addAffineListener(this::setAffine);
		if (wasAffineSet) {
			LOG.debug("Setting viewer affine to {} ({})", affineCopy, wasAffineSet);
			viewer.setAffine(affineCopy);
		}
	}

	public boolean isWasAffineSet() {
		return wasAffineSet;
	}

	public void set(final Viewer3DConfig that)
	{
		this.areMeshesEnabled.set(that.areMeshesEnabled.get());
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
