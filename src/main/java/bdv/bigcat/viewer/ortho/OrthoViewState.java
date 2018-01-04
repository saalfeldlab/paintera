package bdv.bigcat.viewer.ortho;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class OrthoViewState
{

	protected final GridConstraintsManager constraintsManager;

	protected final GlobalTransformManager globalTransform;

	protected final ViewerOptions viewerOptions;

	protected final ArrayList< Converter< ?, ARGBType > > converters;

	protected final ObservableList< SourceAndConverter< ? > > sacs = FXCollections.observableArrayList();

	protected final SimpleObjectProperty< Interpolation > interpolation = new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR );

	protected final SimpleObjectProperty< Source< ? > > currentSource = new SimpleObjectProperty<>( null );

	protected final ObservableMap< Source< ? >, BooleanProperty > visibility;

	private final IntegerProperty time = new SimpleIntegerProperty();

	public OrthoViewState( final ObservableMap< Source< ? >, BooleanProperty > visibility )
	{
		this( ViewerOptions.options(), visibility );
	}

	public OrthoViewState( final ViewerOptions viewerOptions, final ObservableMap< Source< ? >, BooleanProperty > visibility )
	{
		this( viewerOptions, new GlobalTransformManager(), new GridConstraintsManager(), new ArrayList<>(), visibility );
	}

	public OrthoViewState(
			final ViewerOptions viewerOptions,
			final GlobalTransformManager globalTransform,
			final GridConstraintsManager constraintsManager,
			final List< Converter< ?, ARGBType > > converters,
			final ObservableMap< Source< ? >, BooleanProperty > visibility )
	{
		this.viewerOptions = viewerOptions;
		this.globalTransform = globalTransform;
		this.constraintsManager = constraintsManager;
		this.converters = new ArrayList<>();
		this.converters.addAll( converters );
		this.visibility = visibility;
	}

	protected void trackConverters( final ObservableList< SourceAndConverter< ? > > list )
	{
		list.addListener( new UpdateConverters() );
	}

	private class UpdateConverters implements ListChangeListener< SourceAndConverter< ? > >
	{

		@Override
		public void onChanged( final Change< ? extends SourceAndConverter< ? > > c )
		{
			while ( c.next() )
			{
				converters.clear();
				c.getList().stream().map( SourceAndConverter::getConverter ).forEach( converters::add );
			}
		}

	}

	public synchronized void addSource( final SourceAndConverter< ? > source )
	{
		this.sacs.add( source );
	}

	public synchronized void addSources( final Collection< SourceAndConverter< ? > > sources )
	{
		this.sacs.addAll( sources );
	}

	public synchronized void setVisible( final Source< ? > source, final boolean isVisible )
	{
		this.visibility.get( source ).set( isVisible );
	}

	public void addVisibilityListener( final Source< ? > source, final ChangeListener< Boolean > listener )
	{
		this.visibility.get( source ).addListener( listener );
	}

	public synchronized void removeSource( final Source< ? > source )
	{
		sacs.removeAll( sacs.stream().filter( spimSource -> spimSource.getSpimSource().equals( source ) ).collect( Collectors.toList() ) );
	}

	public synchronized void removeAllSources()
	{
		sacs.clear();
	}

	public synchronized List< SourceAndConverter< ? > > getSourcesCopy()
	{
		return new ArrayList<>( sacs );
	}

	public synchronized ObservableList< SourceAndConverter< ? > > getSourcesSynchronizedCopy()
	{
		final ObservableList< SourceAndConverter< ? > > list = FXCollections.observableArrayList();
		sacs.addListener( ( ListChangeListener< SourceAndConverter< ? > > ) change -> {
			while ( change.next() )
				list.setAll( change.getList() );
		} );
		return list;
	}

	public void toggleInterpolation()
	{
		switch ( this.interpolation.get() )
		{
		case NEARESTNEIGHBOR:
			this.interpolation.set( Interpolation.NLINEAR );
			break;
		case NLINEAR:
			this.interpolation.set( Interpolation.NEARESTNEIGHBOR );
			break;
		default:
			this.interpolation.set( Interpolation.NEARESTNEIGHBOR );
			break;
		}
	}

	public ObjectProperty< Source< ? > > currentSourceProperty()
	{
		return this.currentSource;
	}

	public GlobalTransformManager transformManager()
	{
		return this.globalTransform;
	}

	public IntegerProperty timeProperty()
	{
		return this.time;
	}

}
