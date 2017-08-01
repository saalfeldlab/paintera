package bdv.bigcat.viewer.atlas.mode;

import java.util.HashSet;
import java.util.function.Consumer;

import bdv.viewer.ViewerPanel;

public abstract class AbstractStateMode implements Mode
{

	private final HashSet< ViewerPanel > knownViewers = new HashSet<>();

	protected boolean isActive = false;

	@Override
	public Consumer< ViewerPanel > onEnter()
	{
		final Consumer< ViewerPanel > onEnter = getOnEnter();
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
		final Consumer< ViewerPanel > onExit = onExit();
		for ( final ViewerPanel viewer : knownViewers )
			onExit.accept( viewer );
		additionalActionOnDisable();
	}

	protected void additionalActionOnEnable()
	{

	}

	protected void additionalActionOnDisable()
	{

	}

	protected abstract Consumer< ViewerPanel > getOnEnter();

}
