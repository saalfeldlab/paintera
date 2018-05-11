package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import javafx.scene.Group;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class SourceStateSerializer implements JsonSerializer< SourceStateWithIndexedDependencies< ?, ? > >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String TYPE_KEY = "sourceType";

	private static final String STATE_KEY = "state";

	private static final String IS_VISIBLE_KEY = "isVisible";

	private static final String CONVERTER_KEY = "converter";

	private static final String COMPOSITE_KEY = "composite";

	private static final String META_KEY = "meta";

	private static final String NAME_KEY = "name";

	private static final String INTERPOLATION_KEY = "interpolation";

	private static final String SELECTED_IDS_KEY = "selectedIds";

	private static final String TRANSFORM_KEY = "sourceTransform";

	private static final String ASSIGNMENT_KEY = "assignment";

	private static final String CONVERTER_MIN_KEY = "min";

	private static final String CONVERTER_MAX_KEY = "max";

	private static final String CONVERTER_COLOR_KEY = "color";

	private static final String CONVERTER_ALPHA_KEY = "alpha";

	private static final String CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY = "activeFragmentAlpha";

	private static final String CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY = "activeSegmentAlpha";

	private static final String CONVERTER_SEED_KEY = "seed";

	private static final String CONVERTER_COLOR_FROM_SEGMENT_KEY = "colorFromSegment";

	private static final String CANVAS_DIR_KEY = "canvasDir";

	private static final String META_DATA_CLASS_KEY = "class";

	private static final String META_DATA_DATA_KEY = "data";

	private static final String DEPENDS_ON_KEY = "dependsOn";

	public enum StateType
	{
		RAW, LABEL, UNKNOWN;

		public static StateType fromState( final SourceState< ?, ? > state )
		{
			return state instanceof LabelSourceState< ?, ? >
			? LABEL
					: state instanceof RawSourceState< ?, ? >
			? RAW : UNKNOWN;
		}

	};

	@Override
	public JsonElement serialize( final SourceStateWithIndexedDependencies< ?, ? > src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final JsonObject state = new JsonObject();
		final StateType type = StateType.fromState( src.state() );
		state.add( TYPE_KEY, context.serialize( type ) );
		state.add( DEPENDS_ON_KEY, context.serialize( src.dependsOn() ) );
		switch ( type )
		{
		case RAW:
			state.add( STATE_KEY, serializeRaw( ( RawSourceState< ?, ? > ) src.state(), context ) );
			break;
		case LABEL:
			state.add( STATE_KEY, serializeLabel( ( LabelSourceState< ?, ? > ) src.state(), context ) );
			break;
		default:
			break;
		}
		return context.serialize( state );
	}

	private static JsonObject serialize( final SourceState< ?, ? > src, final JsonSerializationContext context )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		src.getDataSource().getSourceTransform( 0, 0, transform );

		final JsonObject map = new JsonObject();
		map.add( IS_VISIBLE_KEY, new JsonPrimitive( src.isVisibleProperty().get() ) );
		map.add( COMPOSITE_KEY, context.serialize( src.compositeProperty().getValue() ) );
		map.add( NAME_KEY, new JsonPrimitive( src.nameProperty().get() ) );
		map.add( INTERPOLATION_KEY, context.serialize( src.interpolationProperty().getValue() ) );
		map.add( TRANSFORM_KEY, context.serialize( transform ) );

		return map;
	}

	private static JsonObject serializeRaw( final RawSourceState< ?, ? > src, final JsonSerializationContext context )
	{
		final JsonObject map = serialize( src, context );
		final Converter< ?, ARGBType > converter = src.getConverter();
		if ( converter instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< ? > c = ( ARGBColorConverter< ? > ) converter;
			final JsonObject converterMap = new JsonObject();
			converterMap.add( CONVERTER_MIN_KEY, new JsonPrimitive( c.getMin() ) );
			converterMap.add( CONVERTER_MAX_KEY, new JsonPrimitive( c.getMax() ) );
			converterMap.add( CONVERTER_ALPHA_KEY, new JsonPrimitive( c.alphaProperty().get() ) );
			converterMap.add( CONVERTER_COLOR_KEY, new JsonPrimitive( c.getColor().get() ) );
			map.add( CONVERTER_KEY, converterMap );
		}

		return map;
	}

	private static JsonObject serializeLabel( final LabelSourceState< ?, ? > src, final JsonSerializationContext context )
	{
		final JsonObject map = serialize( src, context );
		map.add( SELECTED_IDS_KEY, context.serialize( src.selectedIds() ) );
		// TODO do assignment
		map.add( ASSIGNMENT_KEY, context.serialize( src.assignment() ) );

		final Converter< ?, ARGBType > converter = src.getConverter();
		if ( converter instanceof HighlightingStreamConverter< ? > )
		{
			final HighlightingStreamConverter< ? > c = ( HighlightingStreamConverter< ? > ) converter;
			final JsonObject converterMap = new JsonObject();
			converterMap.add( CONVERTER_ALPHA_KEY, new JsonPrimitive( c.alphaProperty().get() ) );
			converterMap.add( CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY, new JsonPrimitive( c.activeFragmentAlphaProperty().get() ) );
			converterMap.add( CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY, new JsonPrimitive( c.activeSegmentAlphaProperty().get() ) );
			converterMap.add( CONVERTER_SEED_KEY, new JsonPrimitive( c.seedProperty().get() ) );
			converterMap.add( CONVERTER_COLOR_FROM_SEGMENT_KEY, new JsonPrimitive( c.colorFromSegmentIdProperty().get() ) );
			map.add( CONVERTER_KEY, converterMap );
		}

		return map;
	}

	private static < D extends NativeType< D > & RealType< D >, T extends Volatile< D > & RealType< T > & NativeType< T > > RawSourceState< D, T > rawFromMap(
			final JsonObject map,
			final SharedQueue queue,
			final int priority,
			final Gson gson,
			final SourceState< ?, ? >... dependencies ) throws JsonSyntaxException, ClassNotFoundException
	{

		LOG.warn( "Getting raw from {}", map );
		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.debug( "got meta data: {}", metaData );
		@SuppressWarnings( "unchecked" )
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = gson.fromJson( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		@SuppressWarnings( "unchecked" )
		final Composite< ARGBType, ARGBType > composite = gson.fromJson( map.get( COMPOSITE_KEY ), Composite.class );

		final RawSourceState< D, T > state = null;

		state.compositeProperty().set( composite );

		state.nameProperty().set( name );

		if ( state.converter() instanceof ARGBColorConverter )
		{
			final ARGBColorConverter< ? > converter = state.converter();
			if ( map.has( CONVERTER_KEY ) )
			{
				final JsonObject converterSettings = map.get( CONVERTER_KEY ).getAsJsonObject();
				Optional.ofNullable( converterSettings.get( CONVERTER_MIN_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMin );
				Optional.ofNullable( converterSettings.get( CONVERTER_MAX_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMax );
				Optional.ofNullable( converterSettings.get( CONVERTER_COLOR_KEY ) ).map( JsonElement::getAsInt ).map( ARGBType::new ).ifPresent( converter::setColor );
				Optional.ofNullable( converterSettings.get( CONVERTER_ALPHA_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter.alphaProperty()::set );
			}
			else
			{
				converter.setMin( state.getDataSource().getType().getMinValue() );
				converter.setMax( state.getDataSource().getType().getMaxValue() );
			}
		}

		state.interpolationProperty().set( Optional
				.ofNullable( gson.fromJson( map.get( INTERPOLATION_KEY ), Interpolation.class ) )
				.orElse( Interpolation.NEARESTNEIGHBOR ) );
		return state;
	}

	private static LabelSourceState< LabelMultisetType, VolatileLabelMultisetType > labelFromMap(
			final JsonObject map,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Gson gson,
			final SourceState< ?, ? >... dependencies ) throws IOException, JsonParseException, ClassNotFoundException
	{
		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.warn( "got meta data: {}", metaData );
		@SuppressWarnings( "unchecked" )
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = gson.fromJson( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		@SuppressWarnings( "unchecked" )
		final Composite< ARGBType, ARGBType > composite = gson.fromJson( map.get( COMPOSITE_KEY ), Composite.class );

		final SelectedIds selectedIds = Optional
				.ofNullable( map.get( SELECTED_IDS_KEY ) )
				.map( o -> gson.fromJson( o, SelectedIds.class ) )
				.orElse( new SelectedIds() );
		final TmpDirectoryCreator nextCanvasDirectory = new TmpDirectoryCreator( null, null );
		final String canvasDir = Optional.ofNullable( map.get( CANVAS_DIR_KEY ) ).map( JsonElement::getAsString ).orElseGet( nextCanvasDirectory );

		final JsonObject serializedAssignment = map.get( ASSIGNMENT_KEY ).getAsJsonObject();
		final Optional< Pair< long[], long[] > > initialAssignment = serializedAssignment == null
				? Optional.empty()
						: Optional.of( new ValuePair<>(
								gson.fromJson( serializedAssignment.get( FragmentSegmentAssignmentOnlyLocalSerializer.FRAGMENTS_KEY ), long[].class ),
								gson.fromJson( serializedAssignment.get( FragmentSegmentAssignmentOnlyLocalSerializer.SEGMENTS_KEY ), long[].class ) ) );

				final LabelSourceState< LabelMultisetType, VolatileLabelMultisetType > state = null;

				if ( state.converter() instanceof HighlightingStreamConverter< ? > && map.has( CONVERTER_KEY ) )
				{
					final HighlightingStreamConverter< ? > converter = state.converter();
					Optional.ofNullable( map.get( CONVERTER_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.alphaProperty()::set );
					Optional.ofNullable( map.get( CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.activeFragmentAlphaProperty()::set );
					Optional.ofNullable( map.get( CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.activeSegmentAlphaProperty()::set );
					Optional.ofNullable( map.get( CONVERTER_SEED_KEY ) ).map( JsonElement::getAsLong ).ifPresent( converter.seedProperty()::set );
					Optional.ofNullable( map.get( CONVERTER_COLOR_FROM_SEGMENT_KEY ) ).map( JsonElement::getAsBoolean ).ifPresent( converter.colorFromSegmentIdProperty()::set );
				}
				state.interpolationProperty().set( Optional
						.ofNullable( gson.fromJson( map.get( INTERPOLATION_KEY ), Interpolation.class ) )
						.orElse( Interpolation.NEARESTNEIGHBOR ) );
				return state;

	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static < D, T > SourceState< D, T > deserializeState(
			final JsonObject serializedSource,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Gson gson,
			final SourceState< ?, ? >... dependencies ) throws IncompatibleTypeException, JsonParseException, ClassNotFoundException, IOException
	{
		final StateType type = gson.fromJson( serializedSource.get( TYPE_KEY ), StateType.class );
		switch ( type )
		{
		case RAW:
			return ( RawSourceState ) rawFromMap(
					serializedSource.get( STATE_KEY ).getAsJsonObject(),
					queue,
					priority,
					gson,
					dependencies );
		case LABEL:
			return ( LabelSourceState ) labelFromMap(
					serializedSource.get( STATE_KEY ).getAsJsonObject(),
					queue, priority,
					root,
					propagationExecutor,
					manager,
					workers,
					gson,
					dependencies );
		default:
			break;
		}
		throw new IncompatibleTypeException( type, "Supported types are: " + Arrays.asList( StateType.values() ) );
	}

	public static SourceState< ?, ? >[] makeStates(
			final JsonArray serializedStates,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final Gson gson ) throws ClassNotFoundException, UndefinedDependency, HasCyclicDependencies, IncompatibleTypeException, JsonParseException, IOException
	{
//		final Meta[] metas = new RawMeta[ serializedStates.size() ];
		final int numStates = serializedStates.size();
		final TIntHashSet[] dependsOn = new TIntHashSet[ numStates ];
		LOG.warn( "Deserializing {}", serializedStates );
		for ( int i = 0; i < numStates; ++i )
		{
			final JsonObject map = serializedStates.get( i ).getAsJsonObject().get( STATE_KEY ).getAsJsonObject();
			LOG.warn( "Deserializing state {}: {}", i, map );
			final JsonElement metaTest = map.get( META_KEY );
			LOG.warn( "Meta test: {}", metaTest );
			final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
			LOG.debug( "got meta data: {}", metaData );
//			metas[ i ] = ( Meta ) gson.fromJson(
//					metaData.get( META_DATA_DATA_KEY ),
//					Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
			final int[] depends = Optional
					.ofNullable( serializedStates.get( i ).getAsJsonObject().get( DEPENDS_ON_KEY ) )
					.map( el -> gson.fromJson( el, int[].class ) )
					.orElseGet( () -> new int[] {} );
			if ( Arrays.stream( depends ).filter( d -> d < 0 || d >= numStates ).count() > 0 ) { throw new UndefinedDependency( depends, numStates ); }
			dependsOn[ i ] = new TIntHashSet( depends );
		}

		if ( hasCycles( dependsOn ) ) { throw new HasCyclicDependencies( dependsOn ); }

		final SourceState< ?, ? >[] sourceStates = new SourceState[ numStates ];

		for ( int i = 0; i < numStates && Arrays.stream( sourceStates ).filter( s -> s == null ).count() > 0; ++i )
		{
			for ( int k = 0; k < numStates; ++k )
			{
				if ( sourceStates[ k ] == null )
				{
					final SourceState< ?, ? >[] dependencies = IntStream.of( dependsOn[ k ].toArray() ).mapToObj( m -> sourceStates[ m ] ).toArray( SourceState[]::new );
					if ( Stream.of( dependencies ).filter( s -> s == null ).count() == 0 )
					{
						sourceStates[ k ] = deserializeState(
								serializedStates.get( k ).getAsJsonObject(),
								queue,
								priority,
								root,
								propagationExecutor,
								manager,
								workers,
								gson,
								dependencies );
					}
				}
			}
		}

		if ( Arrays.stream( sourceStates ).filter( s -> s == null ).count() > 0 )
		{
			throw new RuntimeException( "OOPS!" );
		}

		return sourceStates;

	}

	private static boolean hasCycles( final TIntHashSet[] nodeEdgeMap )
	{
		final TIntHashSet visitedNodes = new TIntHashSet();
		for ( int node = 0; node < nodeEdgeMap.length; ++node )
		{
			visit( nodeEdgeMap, node, visitedNodes );
		}
		return false;
	}

	private static boolean visit(
			final TIntHashSet[] nodeEdgeMap,
			final int node,
			final TIntHashSet hasVisited )
	{
		if ( hasVisited.contains( node ) ) { return true; }
		hasVisited.add( node );
		for ( final TIntIterator it = nodeEdgeMap[ node ].iterator(); it.hasNext(); )
		{
			final boolean foundAlreadyVisitedNode = visit( nodeEdgeMap, it.next(), hasVisited );
			if ( foundAlreadyVisitedNode ) { return foundAlreadyVisitedNode; }
		}
		return false;
	}

}
