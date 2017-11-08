package bdv.bigcat.viewer.atlas;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.viewer.Source;

public class SourceInfo
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, Function< ?, ForegroundCheck< ? > > > foregroundChecks = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState > frags = new HashMap<>();

	private final HashMap< Source< ? >, HashMap< Mode, ARGBStream > > streams = new HashMap<>();

	private final HashMap< Source< ? >, HashMap< Mode, SelectedIds > > selectedIds = new HashMap<>();

	public synchronized void addRawSource(
			final Source< ? > source,
			final Source< ? > dataSource )
	{
		addSource( source, dataSource, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty() );
	}

	public synchronized < T, U > void addLabelSource(
			final Source< T > source,
			final Source< U > dataSource,
			final ToIdConverter idConverter,
			final Function foregroundCheck,
			final FragmentSegmentAssignmentState frag,
			final HashMap< Mode, ARGBStream > stream,
			final HashMap< Mode, SelectedIds > selectedId )
	{
		addSource( source, dataSource, Optional.of( idConverter ), Optional.of( foregroundCheck ), Optional.of( frag ), Optional.of( stream ), Optional.of( selectedId ) );
	}

	public synchronized void addSource(
			final Source< ? > source,
			final Source< ? > dataSource,
			final Optional< ToIdConverter > idConverter,
			final Optional< Function > foregroundCheck,
			final Optional< FragmentSegmentAssignmentState > frag,
			final Optional< HashMap< Mode, ARGBStream > > stream,
			final Optional< HashMap< Mode, SelectedIds > > selectedId )
	{
		this.dataSources.put( source, dataSource );
		idConverter.ifPresent( conv -> this.toIdConverters.put( source, conv ) );
		foregroundCheck.ifPresent( f -> this.foregroundChecks.put( source, f ) );
		frag.ifPresent( f -> this.frags.put( source, f ) );
		stream.ifPresent( s -> this.streams.put( source, s ) );
		selectedId.ifPresent( id -> this.selectedIds.put( source, id ) );
	}

	public synchronized void removeSource( final Source< ? > source )
	{
		dataSources.remove( source );
		toIdConverters.remove( source );
		foregroundChecks.remove( source );
		frags.remove( source );
		this.streams.remove( source );
		this.selectedIds.remove( source );
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

	public synchronized Source< ? > dataSource( final Source< ? > source )
	{
		return dataSources.get( source );
	}

	public synchronized Optional< ToIdConverter > toIdConverter( final Source< ? > source )
	{
		return Optional.ofNullable( toIdConverters.get( source ) );
	}

	public synchronized Optional< Function< ?, ForegroundCheck< ? > > > foregroundCheck( final Source< ? > source )
	{
		return Optional.ofNullable( foregroundChecks.get( source ) );
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

}
