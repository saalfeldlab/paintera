package bdv.bigcat.viewer.atlas.source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState.LabelSourceState;
import bdv.bigcat.viewer.atlas.source.AtlasSourceState.RawSourceState;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;

public class SourceInfo
{

	private final ObservableMap< Source< ? >, AtlasSourceState< ?, ? > > states = FXCollections.observableHashMap();

	private final ObservableList< Source< ? > > sources = FXCollections.observableArrayList();

	public < D, T extends RealType< T > > void addRawSource(
			final DataSource< D, T > source,
			final double min,
			final double max,
			final Color color )
	{
		final RawSourceState< T, D > state = new AtlasSourceState.RawSourceState<>( source, min, max );
		state.colorProperty().set( color );
		System.out.println( "ADDING SOURCE: " + source + " " + state );
		addState( source, state );
	}

	public < D, T, F extends FragmentSegmentAssignmentState< F > > void addLabelSource(
			final DataSource< D, T > source,
			final ToIdConverter idConverter,
			final Function< D, Converter< D, BoolType > > toBoolConverter,
			final F frag,
			final HashMap< Mode, ARGBStream > stream,
			final HashMap< Mode, SelectedIds > selectedId )
	{
		final LabelSourceState< T, D > state = new AtlasSourceState.LabelSourceState<>( source );
		state.toIdConverterProperty().set( idConverter );
		state.maskGeneratorProperty().set( toBoolConverter );
		state.assignmentProperty().set( frag );
		state.streams().clear();
		state.streams().putAll( stream );
		state.selectedIds().clear();
		state.selectedIds().putAll( selectedId );
		addState( source, state );
	}

	private synchronized < D, T > void addState( final Source< T > source, final AtlasSourceState< T, D > state )
	{
		this.states.put( source, state );
		this.sources.add( source );
	}

	public synchronized < D, T > void removeSource( final DataSource< D, T > source )
	{
		this.states.remove( source );
		this.sources.remove( source );
	}

	public synchronized void addMode( final Mode mode, final Function< Source< ? >, Optional< ARGBStream > > makeStream, final Function< Source< ? >, Optional< SelectedIds > > makeSelection )
	{
		for ( final Entry< Source< ? >, AtlasSourceState< ?, ? > > sourceAndState : states.entrySet() )
			if ( sourceAndState.getValue() instanceof LabelSourceState< ?, ? > )
			{
				final LabelSourceState< ?, ? > lstate = ( LabelSourceState< ?, ? > ) sourceAndState.getValue();
				makeStream.apply( sourceAndState.getKey() ).ifPresent( s -> lstate.streams().put( mode, s ) );
				makeSelection.apply( sourceAndState.getKey() ).ifPresent( s -> lstate.selectedIds().put( mode, s ) );
			}
	}

	public synchronized void removeMode( final Mode mode )
	{
		this.states.values().stream().filter( s -> s instanceof LabelSourceState< ?, ? > ).map( s -> ( LabelSourceState< ?, ? > ) s ).forEach( s -> s.streams().remove( mode ) );
		this.states.values().stream().filter( s -> s instanceof LabelSourceState< ?, ? > ).map( s -> ( LabelSourceState< ?, ? > ) s ).forEach( s -> s.selectedIds().remove( mode ) );
	}

	public synchronized Optional< ToIdConverter > toIdConverter( final Source< ? > source )
	{
		final AtlasSourceState< ?, ? > state = states.get( source );
		return state == null || !( state instanceof LabelSourceState< ?, ? > ) ? Optional.empty() : Optional.ofNullable( ( ( LabelSourceState< ?, ? > ) state ).toIdConverterProperty().get() );
	}

	public synchronized Optional< Function< ?, Converter< ?, BoolType > > > toBoolConverter( final Source< ? > source )
	{
		final AtlasSourceState< ?, ? > state = states.get( source );
		return state == null ? Optional.empty() : Optional.ofNullable( ( Function< ?, Converter< ?, BoolType > > ) ( Function ) state.maskGeneratorProperty().get() );
	}

	public synchronized Optional< ? extends FragmentSegmentAssignmentState< ? > > assignment( final Source< ? > source )
	{
		final AtlasSourceState< ?, ? > state = states.get( source );
		return state != null && state instanceof LabelSourceState< ?, ? > ? Optional.of( ( ( LabelSourceState< ?, ? > ) state ).assignmentProperty().get() ) : Optional.empty();
	}

	public synchronized Optional< ARGBStream > stream( final Source< ? > source, final Mode mode )
	{
		final AtlasSourceState< ?, ? > state = states.get( source );
		return state == null || !( state instanceof LabelSourceState< ?, ? > ) ? Optional.empty() : Optional.ofNullable( ( ( LabelSourceState< ?, ? > ) state ).streams().get( mode ) );
	}

	public synchronized Optional< SelectedIds > selectedIds( final Source< ? > source, final Mode mode )
	{
		final AtlasSourceState< ?, ? > state = states.get( source );
		return state == null || !( state instanceof LabelSourceState< ?, ? > ) ? Optional.empty() : Optional.ofNullable( ( ( LabelSourceState< ?, ? > ) state ).selectedIds().get( mode ) );
	}

	public synchronized void forEachStream( final Source< ? > source, final Consumer< ARGBStream > actor )
	{
		Optional
				.ofNullable( states.get( source ) )
				.filter( LabelSourceState.class::isInstance )
				.map( s -> ( ( LabelSourceState< ?, ? > ) s ).streams() )
				.map( Map::values ).orElseGet( () -> new ArrayList<>() ).stream().forEach( actor );
	}

//	public void listenOnVisibilityChange( final MapChangeListener< Source< ? >, Boolean > listener )
//	{
//		this.visibility.addListener( listener );
//	}
//
//	public void stopListeningOnVisibilityChange( final MapChangeListener< Source< ? >, Boolean > listener )
//	{
//		this.visibility.removeListener( listener );
//	}
//
	public ObservableMap< Source< ? >, BooleanProperty > visibility()
	{
		final ObservableMap< Source< ? >, BooleanProperty > visibility = FXCollections.observableHashMap();
		this.states.addListener( ( MapChangeListener< Source< ? >, AtlasSourceState< ?, ? > > ) change -> {
			visibility.clear();
			change.getMap().forEach( ( k, v ) -> visibility.put( k, v.visibleProperty() ) );
		} );
		return visibility;
	}

	public int numSources()
	{
		return this.states.size();
	}

	public AtlasSourceState< ?, ? > getState( final Source< ? > source )
	{
		return states.get( source );
	}

	public ObservableList< Source< ? > > trackSources()
	{
		final ObservableList< Source< ? > > sources = FXCollections.observableArrayList();
		this.sources.addListener( ( ListChangeListener< Source< ? > > ) change -> {
			while ( change.next() )
				sources.setAll( change.getList() );
		} );
		return sources;
	}

}
