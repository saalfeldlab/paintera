package bdv.bigcat.viewer.atlas;

import java.util.function.Consumer;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public class OnEnterOnExit
{

	final Consumer< ViewerPanelFX > onEnter;

	final Consumer< ViewerPanelFX > onExit;

	public OnEnterOnExit( final Consumer< ViewerPanelFX > onEnter, final Consumer< ViewerPanelFX > onExit )
	{
		super();
		this.onEnter = onEnter;
		this.onExit = onExit;
	}

	public Consumer< ViewerPanelFX > onEnter()
	{
		return this.onEnter;
	}

	public Consumer< ViewerPanelFX > onExit()
	{
		return this.onExit;
	}

}