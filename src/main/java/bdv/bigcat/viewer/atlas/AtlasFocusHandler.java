package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public class AtlasFocusHandler
{

	private final static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static class OnEnterOnExit
	{

		private final Consumer< ViewerPanelFX > onEnter;

		private final Consumer< ViewerPanelFX > onExit;

		public OnEnterOnExit( final Consumer< ViewerPanelFX > onEnter, final Consumer< ViewerPanelFX > onExit )
		{
			super();
			this.onEnter = onEnter;
			this.onExit = onExit;
		}

	}

	private final HashSet< OnEnterOnExit > installOnExitRemovables;

	private final HashSet< OnEnterOnExit > installPermanent;

	private final HashMap< ViewerPanelFX, HashSet< OnEnterOnExit > > installed;

	public AtlasFocusHandler()
	{
		super();
		this.installOnExitRemovables = new HashSet<>();
		this.installPermanent = new HashSet<>();
		this.installed = new HashMap<>();
	}

	public synchronized void add( final OnEnterOnExit element, final boolean onExitRemovable )
	{
		if ( onExitRemovable )
			this.installOnExitRemovables.add( element );
		else
			this.installPermanent.add( element );
	}

	public synchronized void remove( final OnEnterOnExit element )
	{
		this.installOnExitRemovables.remove( element );
		this.installPermanent.remove( element );
	}

	public Consumer< ViewerPanelFX > onEnter()
	{
		return new OnEnter();
	}

	public Consumer< ViewerPanelFX > onExit()
	{
		return new OnExit();
	}

	private class OnEnter implements Consumer< ViewerPanelFX >
	{

		@Override
		public void accept( final ViewerPanelFX t )
		{
			LOG.debug( "ENTERING? {} {}", installOnExitRemovables, installPermanent );
			synchronized ( AtlasFocusHandler.this )
			{
				installOnExitRemovables.forEach( consumer -> consumer.onEnter.accept( t ) );
				installPermanent.forEach( consumer -> {
					if ( !installed.containsKey( t ) )
						installed.put( t, new HashSet<>() );
					final HashSet< OnEnterOnExit > installedForViewer = installed.get( t );
					if ( !installedForViewer.contains( consumer ) )
					{
						consumer.onEnter.accept( t );
						installedForViewer.add( consumer );
					}
				} );
			}
		}
	}

	private class OnExit implements Consumer< ViewerPanelFX >
	{

		@Override
		public void accept( final ViewerPanelFX t )
		{
			synchronized ( AtlasFocusHandler.this )
			{
				installOnExitRemovables.forEach( consumer -> consumer.onExit.accept( t ) );
			}
		}
	}

}
