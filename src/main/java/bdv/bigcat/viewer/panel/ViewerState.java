package bdv.bigcat.viewer.panel;

import java.util.Collection;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class ViewerState
{
	private final ViewerPanelFX viewer;

	private final SourcesListener sacs = new SourcesListener();

	// TODO extract interpolation handling into SourceInfo
	private final InterpolationListener interpolation = new InterpolationListener();

	@SuppressWarnings( "unchecked" )
	private final SimpleObjectProperty< GlobalTransformManager > globalTransform = new SimpleObjectProperty<>( new GlobalTransformManager() );

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
		setSources( state.sacs.observable, state.interpolation.observable );
	}

	public synchronized void setSources(
			final ObservableList< SourceAndConverter< ? > > sacs,
			final ObjectProperty< Interpolation > interpolation )
	{
		this.sacs.replaceObservable( sacs );
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

			replaceViewerSources( c.getList() );
		}

		private void replaceViewerSources( final Collection< ? extends SourceAndConverter< ? > > sources )
		{
			viewer.setAllSources( sources );
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
