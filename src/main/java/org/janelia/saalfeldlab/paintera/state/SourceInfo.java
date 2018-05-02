package org.janelia.saalfeldlab.paintera.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class SourceInfo
{

	private final ObservableMap< Source< ? >, SourceState< ?, ? > > states = FXCollections.observableHashMap();

	private final ObservableList< Source< ? > > sources = FXCollections.observableArrayList();

	private final ObservableList< Source< ? > > sourcesReadOnly = FXCollections.unmodifiableObservableList( sources );

	private final ObjectProperty< Source< ? > > currentSource = new SimpleObjectProperty<>( null );

	private final ObservableList< Source< ? > > visibleSources = FXCollections.observableArrayList();

	private final IntegerBinding numSources = Bindings.size( sources );

	private final IntegerBinding numVisibleSources = Bindings.size( visibleSources );

	private final ObservableBooleanValue hasSources = numSources.greaterThan( 0 );

	private final ObservableBooleanValue hasVisibleSources = numSources.greaterThan( 0 );

	private final ObservableList< Source< ? > > visibleSourcesReadOnly = FXCollections.unmodifiableObservableList( visibleSources );
	{
		sources.addListener( ( ListChangeListener< Source< ? > > ) change -> updateVisibleSources() );
	}

	private final ObservableList< SourceAndConverter< ? > > visibleSourcesAndConverter = FXCollections.observableArrayList();

	private final ObservableList< SourceAndConverter< ? > > visibleSourcesAndConverterReadOnly = FXCollections.unmodifiableObservableList( visibleSourcesAndConverter );
	{
		visibleSources.addListener( ( ListChangeListener< Source< ? > > ) change -> updateVisibleSourcesAndConverters() );
	}

	private final IntegerProperty currentSourceIndex = new SimpleIntegerProperty( -1 );
	{
		this.currentSource.addListener( ( oldv, obs, newv ) -> this.currentSourceIndex.set( this.sources.indexOf( newv ) ) );
		this.currentSourceIndex.addListener( ( oldv, obs, newv ) -> this.currentSource.set( newv.intValue() < 0 || newv.intValue() >= this.sources.size() ? null : this.sources.get( newv.intValue() ) ) );
	}

	private final IntegerProperty currentSourceIndexInVisibleSources = new SimpleIntegerProperty();
	{
		this.currentSource.addListener( ( oldv, obs, newv ) -> updateCurrentSourceIndexInVisibleSources() );
		this.visibleSources.addListener( ( ListChangeListener< Source< ? > > ) ( change ) -> updateCurrentSourceIndexInVisibleSources() );
		updateCurrentSourceIndexInVisibleSources();
	}

	private final ObservableMap< Source< ? >, Composite< ARGBType, ARGBType > > composites = FXCollections.observableHashMap();

	private final ObservableMap< Source< ? >, Composite< ARGBType, ARGBType > > compositesReadOnly = FXCollections.unmodifiableObservableMap( composites );

	private final ObservableList< Source< ? > > removedSources = FXCollections.observableArrayList();

	private final ObservableList< Source< ? > > unmodifiableRemovedSources = FXCollections.unmodifiableObservableList( removedSources );
	{
		removedSources.addListener( ( ListChangeListener< Source< ? > > ) change -> removedSources.clear() );
	}

	public < D extends Type< D >, T extends RealType< T > > SourceState< D, T > makeRawSourceState(
			final DataSource< D, T > source,
			final double min,
			final double max,
			final ARGBType color,
			final Composite< ARGBType, ARGBType > composite,
			final Object metaData )
	{
		final ARGBColorConverter< T > converter = new ARGBColorConverter.InvertingImp1<>( min, max );
		converter.colorProperty().set( color );
		final SourceState< D, T > state = new SourceState<>( source, converter, composite, source.getName(), metaData );
		return state;
	}

	public < D extends Type< D >, T extends RealType< T > > SourceState< D, T > addRawSource(
			final DataSource< D, T > source,
			final double min,
			final double max,
			final ARGBType color,
			final Composite< ARGBType, ARGBType > composite,
			final Object metaData )
	{
		final SourceState< D, T > state = makeRawSourceState( source, min, max, color, composite, metaData );
		addState( source, state );
		return state;
	}

	public < D extends Type< D >, T extends Type< T > > SourceState< D, T > makeGenericSourceState(
			final DataSource< D, T > source,
			final Converter< T, ARGBType > converter,
			final Composite< ARGBType, ARGBType > composite )
	{

		return new SourceState<>( source, converter, composite, source.getName(), null );
	}

	public < D extends Type< D >, T extends Type< T > > LabelSourceState< D, T > makeLabelSourceState(
			final DataSource< D, T > source,
			final Converter< T, ARGBType > converter,
			final Composite< ARGBType, ARGBType > composite,
			final Object metaData,
			final LongFunction< Converter< D, BoolType > > maskForLabel,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter,
			final SelectedIds selectedIds,
			final IdService idService,
			final MeshManager meshManager,
			final MeshInfos meshInfos )
	{
		final LabelSourceState< D, T > state = new LabelSourceState<>(
				source,
				converter,
				composite,
				source.getName(),
				metaData,
				maskForLabel,
				assignment,
				toIdConverter,
				selectedIds,
				idService,
				meshManager,
				meshInfos );
		return state;
	}

	public < D extends Type< D >, T extends Type< T > > LabelSourceState< D, T > addLabelSource(
			final DataSource< D, T > source,
			final Converter< T, ARGBType > converter,
			final Composite< ARGBType, ARGBType > composite,
			final Object metaData,
			final LongFunction< Converter< D, BoolType > > maskForLabel,
			final FragmentSegmentAssignmentState assignment,
			final ToIdConverter toIdConverter,
			final SelectedIds selectedIds,
			final IdService idService,
			final MeshManager meshManager,
			final MeshInfos meshInfos )
	{
		final LabelSourceState< D, T > state = makeLabelSourceState(
				source,
				converter,
				composite,
				metaData,
				maskForLabel,
				assignment,
				toIdConverter,
				selectedIds,
				idService,
				meshManager,
				meshInfos );
		addState( source, state );
		return state;
	}

	public synchronized < D extends Type< D >, T extends Type< T > > void addState(
			final Source< T > source,
			final SourceState< D, T > state )
	{
		this.states.put( source, state );
		// composites needs to hold a valid (!=null) value for source whenever
		// viewer is updated
		this.composites.put( source, state.compositeProperty().get() );
		this.sources.add( source );
		state.isVisibleProperty().addListener( ( obs, oldv, newv ) -> updateVisibleSources() );
		state.isVisibleProperty().set( true );
		if ( this.currentSource.get() == null )
		{
			this.currentSource.set( source );
		}
		state.compositeProperty().addListener( ( obs, oldv, newv ) -> this.composites.put( source, newv ) );
	}

	public synchronized < T > void removeSource( final Source< T > source )
	{
		final int currentSourceIndex = this.sources.indexOf( source );
		final SourceState< ?, ? > state = this.states.remove( source );
		if ( state != null && state instanceof LabelSourceState< ?, ? > )
		{
			( ( LabelSourceState< ?, ? > ) state ).meshManager().removeAllMeshes();
		}
		this.sources.remove( source );
		this.currentSource.set( this.sources.size() == 0 ? null : this.sources.get( Math.max( currentSourceIndex - 1, 0 ) ) );
		this.composites.remove( source );
		this.removedSources.add( source );
	}

	public synchronized Optional< ToIdConverter > toIdConverter( final Source< ? > source )
	{
		final SourceState< ?, ? > state = states.get( source );
		return state instanceof LabelSourceState< ?, ? >
				? Optional.of( ( ( LabelSourceState< ?, ? > ) state ).toIdConverter() )
				: Optional.empty();
	}

	public synchronized Optional< FragmentSegmentAssignmentState > assignment( final Source< ? > source )
	{
		final SourceState< ?, ? > state = states.get( source );
		return state instanceof LabelSourceState< ?, ? >
				? Optional.of( ( ( LabelSourceState< ?, ? > ) state ).assignment() )
				: Optional.empty();
	}

	public SourceState< ?, ? > getState( final Source< ? > source )
	{
		return states.get( source );
	}

	public ObservableList< Source< ? > > trackSources()
	{
		return this.sourcesReadOnly;
	}

	public ObservableList< Source< ? > > trackVisibleSources()
	{
		return this.visibleSourcesReadOnly;
	}

	public ObservableList< SourceAndConverter< ? > > trackVisibleSourcesAndConverters()
	{
		return this.visibleSourcesAndConverterReadOnly;
	}

	public ObjectProperty< Source< ? > > currentSourceProperty()
	{
		return this.currentSource;
	}

	public IntegerProperty currentSourceIndexProperty()
	{
		return this.currentSourceIndex;
	}

	public void moveSourceTo( final Source< ? > source, final int index )
	{
		if ( index >= 0 && index < sources.size() && sources.contains( source ) && sources.indexOf( source ) != index )
		{
			final ArrayList< Source< ? > > copy = new ArrayList<>( this.sources );
			copy.remove( source );
			copy.add( index, source );
			final Source< ? > currentSource = this.currentSource.get();
			final int currentSourceIndex = copy.indexOf( currentSource );
			this.sources.clear();
			this.sources.setAll( copy );
			this.currentSource.set( currentSource );
			this.currentSourceIndex.set( currentSourceIndex );
		}
	}

	public void moveSourceTo( final int from, final int to )
	{
		if ( from >= 0 && from < sources.size() )
		{
			moveSourceTo( sources.get( from ), to );
		}
	}

	private void modifyCurrentSourceIndex( final int amount )
	{
		if ( this.sources.size() == 0 )
		{
			this.currentSourceIndex.set( -1 );
		}
		else
		{
			final int newIndex = ( this.currentSourceIndex.get() + amount ) % this.sources.size();
			this.currentSourceIndex.set( newIndex < 0 ? this.sources.size() + newIndex : newIndex );
		}
	}

	public void incrementCurrentSourceIndex()
	{
		modifyCurrentSourceIndex( 1 );
	}

	public void decrementCurrentSourceIndex()
	{
		modifyCurrentSourceIndex( -1 );
	}

	public ObservableBooleanValue isCurrentSource( final Source< ? > source )
	{
		return Bindings.createBooleanBinding(
				() -> Optional.ofNullable( currentSource.get() ).map( source::equals ).orElse( false ),
				currentSource );
	}

	public ObservableIntegerValue currentSourceIndexInVisibleSources()
	{
		return this.currentSourceIndexInVisibleSources;
	}

	private void updateVisibleSources()
	{

		final List< Source< ? > > visibleSources = this.sources
				.stream()
				.filter( s -> states.get( s ).isVisibleProperty().get() )
				.collect( Collectors.toList() );
		this.visibleSources.setAll( visibleSources );
	}

	private void updateVisibleSourcesAndConverters()
	{
		this.visibleSourcesAndConverter.setAll( this.visibleSources
				.stream()
				.map( states::get )
				.map( SourceState::getSourceAndConverter )
				.collect( Collectors.toList() ) );
	}

	private void updateCurrentSourceIndexInVisibleSources()
	{
		this.currentSourceIndexInVisibleSources.set( this.visibleSources.indexOf( currentSource.get() ) );
	}

	public ObservableMap< Source< ? >, Composite< ARGBType, ARGBType > > composites()
	{
		return this.compositesReadOnly;
	}

	public ObservableList< Source< ? > > removedSourcesTracker()
	{
		return this.removedSources;
	}

	public ObservableIntegerValue numSources()
	{
		return this.numSources;
	}

	public ObservableIntegerValue numVisibleSources()
	{
		return this.numVisibleSources;
	}

	public ObservableBooleanValue hasSources()
	{
		return this.hasSources;
	}

	public ObservableBooleanValue hasVisibleSources()
	{
		return this.hasVisibleSources;
	}

}
