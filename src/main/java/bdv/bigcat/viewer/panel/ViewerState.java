package bdv.bigcat.viewer.panel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

public class ViewerState
{
	private final ViewerPanelFX viewer;

	private final SourcesListener sacs = new SourcesListener();

	private final VisibilityListener visibility = new VisibilityListener();

	private final CurrentSourceListener currentSource = new CurrentSourceListener();

	private final InterpolationListener interpolation = new InterpolationListener();

	private final SimpleObjectProperty< GlobalTransformManager > globalTransform = new SimpleObjectProperty<>( new GlobalTransformManager() );

	private final HashMap< Source< ? >, ChangeListener< Boolean > > visibilityListeners = new HashMap<>();

	public ViewerState( final ViewerPanelFX viewer )
	{
		super();
		this.viewer = viewer;
	}

	public synchronized void set( final ViewerState state )
	{
		setGlobalTransform( state );
		setSources( state );
	}

	public synchronized void setGlobalTransform( final ViewerState state )
	{
		setGlobalTransform( state.globalTransform.get() );
	}

	public synchronized void setGlobalTransform( final GlobalTransformManager gm )
	{
		this.globalTransform.set( gm );
	}

	public synchronized Property< GlobalTransformManager > globalTransformProperty()
	{
		return this.globalTransform;
	}

	public synchronized void setSources( final ViewerState state )
	{
		setSources( state.sacs.observable, state.visibility.observable, state.currentSource.observable, state.interpolation.observable );
	}

	public synchronized void setSources(
			final ObservableList< SourceAndConverter< ? > > sacs,
			final ObservableMap< Source< ? >, BooleanProperty > isVisible,
			final ObjectProperty< Source< ? > > currentSource,
			final ObjectProperty< Interpolation > interpolation )
	{
		this.sacs.replaceObservable( sacs );
		this.sacs.observable.stream().map( SourceAndConverter::getSpimSource ).forEach( s -> Optional.ofNullable( visibility.observable.get( s ) ).ifPresent( v -> v.removeListener( visibilityListeners.get( s ) ) ) );
		this.visibilityListeners.clear();
		this.visibility.replaceObservable( isVisible );
		this.currentSource.replaceObservable( currentSource );
		this.interpolation.replaceObservable( interpolation );
	}

	public abstract class ObservableRegisteringChangeListener< T > implements ChangeListener< T >
	{

		protected ObjectProperty< T > observable;

		public ObservableRegisteringChangeListener( final SimpleObjectProperty< T > observable )
		{
			super();
			this.observable = observable;
		}

		public void replaceObservable( final ObjectProperty< T > observable )
		{
			this.observable.removeListener( this );
			this.observable = observable;
			this.observable.addListener( this );
		}

	}

	public class VisibilityListener implements MapChangeListener< Source< ? >, BooleanProperty >
	{

		private ObservableMap< Source< ? >, BooleanProperty > observable = FXCollections.observableHashMap();

		public void replaceObservable( final ObservableMap< Source< ? >, BooleanProperty > observable )
		{
			this.observable.removeListener( this );
			this.observable = observable;
			this.observable.addListener( this );
			this.observable.forEach( ( k, v ) -> {
				if ( !visibilityListeners.containsKey( k ) )
					visibilityListeners.put( k, ( obs, oldv, newv ) -> viewer.getVisibilityAndGrouping().setSourceActive( k, newv.booleanValue() ) );
				v.addListener( visibilityListeners.get( k ) );
			} );
		}

		@Override
		public void onChanged( final Change< ? extends Source< ? >, ? extends BooleanProperty > change )
		{
			if ( change.wasRemoved() )
				change.getValueRemoved().removeListener( visibilityListeners.remove( change.getKey() ) );
			if ( change.wasAdded() )
			{
				if ( !visibilityListeners.containsKey( change.getKey() ) )
					visibilityListeners.put( change.getKey(), ( obs, oldv, newv ) -> viewer.getVisibilityAndGrouping().setSourceActive( change.getKey(), newv.booleanValue() ) );
				change.getValueAdded().addListener( visibilityListeners.get( change.getKey() ) );
			}
		}
	}

	public class CurrentSourceListener extends ObservableRegisteringChangeListener< Source< ? > >
	{

		public CurrentSourceListener()
		{
			super( new SimpleObjectProperty<>( null ) );
		}

		@Override
		public void changed( final ObservableValue< ? extends Source< ? > > observable, final Source< ? > oldValue, final Source< ? > newValue )
		{
			viewer.getVisibilityAndGrouping().setCurrentSource( newValue );
		}

	}

	public class InterpolationListener extends ObservableRegisteringChangeListener< Interpolation >
	{

		public InterpolationListener()
		{
			super( new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR ) );
		}

		@Override
		public void changed( final ObservableValue< ? extends Interpolation > observable, final Interpolation oldValue, final Interpolation newValue )
		{
			viewer.setInterpolation( newValue );
		}

	}

	public class SourcesListener implements ListChangeListener< SourceAndConverter< ? > >
	{

		private ObservableList< SourceAndConverter< ? > > observable = FXCollections.observableArrayList();

		public void replaceObservable( final ObservableList< SourceAndConverter< ? > > observable )
		{
			this.observable.removeListener( this );
			this.observable = observable;
			this.replaceViewerSources( this.observable );
			this.observable.addListener( this );
		}

		@Override
		public void onChanged( final Change< ? extends SourceAndConverter< ? > > c )
		{
			replaceViewerSources( ( List< SourceAndConverter< ? > > ) c.getList() );
		}

		private void replaceViewerSources( final Collection< SourceAndConverter< ? > > sources )
		{
			viewer.removeAllSources();
			viewer.addSources( sources );
		}

	}

	public void setVisibility( final Source< ? > source, final boolean isVisible )
	{
		this.visibility.observable.get( source ).set( isVisible );
	}

	public synchronized void setCurrentSource( final Source< ? > source )
	{
		this.currentSource.observable.set( source );
	}

	public synchronized void addSource( final SourceAndConverter< ? > sac )
	{
		this.sacs.observable.add( sac );
	}

	public synchronized void addSources( final Collection< SourceAndConverter< ? > > sacs )
	{
		this.sacs.observable.addAll( sacs );
	}

	public synchronized void removeSource( final Source< ? > source )
	{
		this.sacs.observable.remove( source );
	}

	public synchronized void removeAllSources()
	{
		this.sacs.observable.clear();
	}

	public List< SourceAndConverter< ? > > getSourcesCopy()
	{
		synchronized ( this.sacs )
		{
			return new ArrayList<>( this.sacs.observable );
		}
	}

	public synchronized void toggleInterpolation()
	{
		final Interpolation interpolation = this.interpolation.observable.get();
		if ( interpolation == null )
			this.interpolation.observable.set( Interpolation.NEARESTNEIGHBOR );
		else
			switch ( interpolation )
			{
			case NEARESTNEIGHBOR:
				this.interpolation.observable.set( Interpolation.NLINEAR );
				break;
			case NLINEAR:
				this.interpolation.observable.set( Interpolation.NEARESTNEIGHBOR );
				break;
			default:
				this.interpolation.observable.set( Interpolation.NEARESTNEIGHBOR );
				break;
			}
	}
}
