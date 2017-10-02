package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;

public class BaseViewState
{

	protected final GridConstraintsManager constraintsManager;

	protected final GlobalTransformManager globalTransform;

	protected final ViewerOptions viewerOptions;

	protected final ArrayList< Converter< ?, ARGBType > > converters;

	protected final ObservableList< SourceAndConverter< ? > > sacs = FXCollections.observableArrayList();

	protected final SimpleObjectProperty< Interpolation > interpolation = new SimpleObjectProperty<>( Interpolation.NEARESTNEIGHBOR );

	protected final SimpleObjectProperty< Optional< Source< ? > > > currentSource = new SimpleObjectProperty<>( Optional.empty() );

	protected final ObservableMap< Source< ? >, Boolean > visibility = FXCollections.observableHashMap();

	public BaseViewState()
	{
		this( ViewerOptions.options() );
	}

	public BaseViewState( final ViewerOptions viewerOptions )
	{
		this( viewerOptions, new GlobalTransformManager(), new GridConstraintsManager(), new ArrayList<>() );
	}

	public BaseViewState( final ViewerOptions viewerOptions, final GlobalTransformManager globalTransform, final GridConstraintsManager constraintsManager, final List< Converter< ?, ARGBType > > converters )
	{
		this.viewerOptions = viewerOptions;
		this.globalTransform = globalTransform;
		this.constraintsManager = constraintsManager;
		this.converters = new ArrayList<>();
		this.converters.addAll( converters );
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
		this.visibility.put( source, isVisible );
	}

	public void addVisibilityListener( final MapChangeListener< Source< ? >, Boolean > listener )
	{
		this.visibility.addListener( listener );
	}

	public void addCurrentSourceListener( final ChangeListener< Optional< Source< ? > > > listener )
	{
		this.currentSource.addListener( listener );
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

}
