package bdv.bigcat.viewer.atlas.mode;

import java.util.function.Consumer;

import bdv.viewer.ViewerPanel;

public class NavigationOnly implements Mode
{

	@Override
	public Consumer< ViewerPanel > onEnter()
	{
		return vp -> {};
	}

	@Override
	public Consumer< ViewerPanel > onExit()
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
