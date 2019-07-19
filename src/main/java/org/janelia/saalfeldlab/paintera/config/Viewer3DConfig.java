package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

public class Viewer3DConfig
{

	private final SimpleBooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	private final SimpleBooleanProperty showBlockBoundaries = new SimpleBooleanProperty(false);

	private final SimpleIntegerProperty rendererBlockSize = new SimpleIntegerProperty(64);

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

	public void bindViewerToConfig(final Viewer3DFX viewer)
	{
		viewer.isMeshesEnabledProperty().bind(this.areMeshesEnabled);
		viewer.showBlockBoundariesProperty().bind(this.showBlockBoundaries);
		viewer.rendererBlockSizeProperty().bind(this.rendererBlockSize);
	}

	public void set(final Viewer3DConfig that)
	{
		this.areMeshesEnabled.set(that.areMeshesEnabled.get());
		this.showBlockBoundaries.set(that.showBlockBoundaries.get());
		this.rendererBlockSize.set(that.rendererBlockSize.get());
	}

}
