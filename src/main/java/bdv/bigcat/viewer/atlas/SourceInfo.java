package bdv.bigcat.viewer.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.viewer.Source;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;

public class SourceInfo
{

//	// map volatile sources to sources
//	private final HashMap< Source< ? >, Source< ? > > sources = new HashMap<>();

	// volatile sources to id converters
	private final HashMap< DataSource< ?, ? >, ToIdConverter > toIdConverters = new HashMap<>();

	// volatile source to foregorund check
	private final HashMap< DataSource< ?, ? >, Function< ?, Converter< ?, BoolType > > > toBoolConverters = new HashMap<>();

	private final HashMap< DataSource< ?, ? >, FragmentSegmentAssignmentState > frags = new HashMap<>();

	private final HashMap< DataSource< ?, ? >, HashMap< Mode, ARGBStream > > streams = new HashMap<>();

	private final HashMap< DataSource< ?, ? >, HashMap< Mode, SelectedIds > > selectedIds = new HashMap<>();

	private final ObservableMap< Source< ? >, Boolean > visibility = FXCollections.observableHashMap();

	public synchronized < D, T > void addRawSource( final DataSource< ?, ? > source )
	{
		addSource( source, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty() );
	}

	public synchronized < D, T > void addLabelSource(
			final DataSource< D, T > source,
			final ToIdConverter idConverter,
			final Function toBoolConverter,
			final FragmentSegmentAssignmentState frag,
			final HashMap< Mode, ARGBStream > stream,
			final HashMap< Mode, SelectedIds > selectedId )
	{
		addSource( source, Optional.of( idConverter ), Optional.of( toBoolConverter ), Optional.of( frag ), Optional.of( stream ), Optional.of( selectedId ) );
	}

	public synchronized < D, T > void addSource(
			final DataSource< D, T > source,
			final Optional< ToIdConverter > idConverter,
			final Optional< Function > toBoolConverter,
			final Optional< FragmentSegmentAssignmentState > frag,
			final Optional< HashMap< Mode, ARGBStream > > stream,
			final Optional< HashMap< Mode, SelectedIds > > selectedId )
	{
		idConverter.ifPresent( conv -> this.toIdConverters.put( source, conv ) );
		toBoolConverter.ifPresent( f -> this.toBoolConverters.put( source, f ) );
		frag.ifPresent( f -> this.frags.put( source, f ) );
		stream.ifPresent( s -> this.streams.put( source, s ) );
		selectedId.ifPresent( id -> this.selectedIds.put( source, id ) );
		this.visibility.put( source, true );
	}

	public synchronized < D, T > void removeSource( final DataSource< D, T > source )
	{
		toIdConverters.remove( source );
		toBoolConverters.remove( source );
		frags.remove( source );
		this.streams.remove( source );
		this.selectedIds.remove( source );
		this.visibility.remove( source );
	}

	public synchronized void addMode( final Mode mode, final Function< Source< ? >, Optional< ARGBStream > > makeStream, final Function< Source< ? >, Optional< SelectedIds > > makeSelection )
	{
		streams.forEach( ( k, v ) -> makeStream.apply( k ).ifPresent( s -> v.put( mode, s ) ) );
		selectedIds.forEach( ( k, v ) -> makeSelection.apply( k ).ifPresent( id -> v.put( mode, id ) ) );
	}

	public synchronized void removeMode( final Mode mode )
	{
		this.streams.values().forEach( hm -> hm.remove( mode ) );
		this.selectedIds.values().forEach( hm -> hm.remove( mode ) );
	}

	public synchronized Optional< ToIdConverter > toIdConverter( final Source< ? > source )
	{
		return Optional.ofNullable( toIdConverters.get( source ) );
	}

	public synchronized Optional< Function< ?, Converter< ?, BoolType > > > toBoolConverter( final Source< ? > source )
	{
		return Optional.ofNullable( toBoolConverters.get( source ) );
	}

	public synchronized Optional< FragmentSegmentAssignmentState > assignment( final Source< ? > source )
	{
		return Optional.ofNullable( frags.get( source ) );
	}

	public synchronized Optional< ARGBStream > stream( final Source< ? > source, final Mode mode )
	{
		return Optional.ofNullable( Optional.ofNullable( streams.get( source ) ).orElse( new HashMap<>() ).get( mode ) );
	}

	public synchronized Optional< SelectedIds > selectedIds( final Source< ? > source, final Mode mode )
	{
		return Optional.ofNullable( Optional.ofNullable( selectedIds.get( source ) ).orElse( new HashMap<>() ).get( mode ) );
	}

	public synchronized void forEachStream( final Source< ? > source, final Consumer< ARGBStream > actor )
	{
		Optional.ofNullable( streams.get( source ) ).map( HashMap::values ).orElseGet( () -> new ArrayList<>() ).stream().forEach( actor );
	}

	public void listenOnVisibilityChange( final MapChangeListener< Source< ? >, Boolean > listener )
	{
		this.visibility.addListener( listener );
	}

	public void stopListeningOnVisibilityChange( final MapChangeListener< Source< ? >, Boolean > listener )
	{
		this.visibility.removeListener( listener );
	}

	public ObservableMap< Source< ? >, Boolean > visibility()
	{
		return this.visibility;
	}

	public int numSources()
	{
		return this.visibility.size();
	}

}
