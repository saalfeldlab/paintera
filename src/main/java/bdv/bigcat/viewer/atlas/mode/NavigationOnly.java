package bdv.bigcat.viewer.atlas.mode;

import java.util.function.Consumer;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public class NavigationOnly implements Mode
{

	@Override
	public Consumer< ViewerPanelFX > onEnter()
	{
		return vp -> {};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return vp -> {};
	}

	@Override
	public void enable()
	{

	}

	@Override
	public void disable()
	{

	}

	@Override
	public String getName()
	{
		return "navigation only";
	}

}
