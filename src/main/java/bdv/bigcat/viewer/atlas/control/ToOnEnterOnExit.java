package bdv.bigcat.viewer.atlas.control;

import java.util.function.Consumer;

import bdv.bigcat.viewer.atlas.AtlasFocusHandler.OnEnterOnExit;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public interface ToOnEnterOnExit
{

	public Consumer< ViewerPanelFX > getOnEnter();

	public Consumer< ViewerPanelFX > getOnExit();

	public default OnEnterOnExit onEnterOnExit()
	{
		return new OnEnterOnExit( getOnEnter(), getOnExit() );
	}

}
