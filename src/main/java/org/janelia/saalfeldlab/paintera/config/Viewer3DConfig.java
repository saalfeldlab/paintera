package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;

public class Viewer3DConfig
{

	private final SimpleBooleanProperty areMeshesEnabled = new SimpleBooleanProperty(true);

	public BooleanProperty areMeshesenabledProperty()
	{
		return this.areMeshesEnabled;
	}

	public void bindViewerToConfig(final Viewer3DFX viewer)
	{
		viewer.isMeshesEnabledProperty().bind(this.areMeshesEnabled);
	}

	public void set(final Viewer3DConfig that)
	{
		this.areMeshesEnabled.set(that.areMeshesEnabled.get());
	}

}
