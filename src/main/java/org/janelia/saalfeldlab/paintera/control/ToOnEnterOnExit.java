package org.janelia.saalfeldlab.paintera.control;

import java.util.function.Consumer;

import bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;

public interface ToOnEnterOnExit
{

	public Consumer<ViewerPanelFX> getOnEnter();

	public Consumer<ViewerPanelFX> getOnExit();

	public default OnEnterOnExit onEnterOnExit()
	{
		return new OnEnterOnExit(getOnEnter(), getOnExit());
	}

}
