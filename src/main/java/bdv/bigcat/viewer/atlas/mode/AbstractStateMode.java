package bdv.bigcat.viewer.atlas.mode;

import java.util.HashSet;
import java.util.function.Consumer;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public abstract class AbstractStateMode implements Mode
{

	private final HashSet< ViewerPanelFX > knownViewers = new HashSet<>();

	protected boolean isActive = false;

	@Override
	public Consumer< ViewerPanelFX > onEnter()
	{
		final Consumer< ViewerPanelFX > onEnter = getOnEnter();
		return vp -> {
			knownViewers.add( vp );
			if ( isActive )
				onEnter.accept( vp );
		};
	}

	@Override
	public void enable()
	{
		isActive = true;
		additionalActionOnEnable();
	}

	@Override
	public void disable()
	{
		isActive = false;
		final Consumer< ViewerPanelFX > onExit = onExit();
		for ( final ViewerPanelFX viewer : knownViewers )
			onExit.accept( viewer );
		additionalActionOnDisable();
	}

	protected void additionalActionOnEnable()
	{

	}

	protected void additionalActionOnDisable()
	{

	}

	protected abstract Consumer< ViewerPanelFX > getOnEnter();

}
