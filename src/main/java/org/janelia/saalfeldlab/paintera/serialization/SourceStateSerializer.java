package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.janelia.saalfeldlab.paintera.data.meta.LabelMeta;
import org.janelia.saalfeldlab.paintera.data.meta.Meta;
import org.janelia.saalfeldlab.paintera.data.meta.exception.MetaException;
import org.janelia.saalfeldlab.paintera.data.meta.exception.SourceCreationFailed;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterLabelMultisetType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Pair;

public class SourceStateSerializer implements JsonSerializer< SourceState< ?, ? > >
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

	public enum StateType
	{
		RAW, LABEL;

		public static StateType fromState( final SourceState< ?, ? > state )
		{
			return state instanceof LabelSourceState< ?, ? > ? LABEL : RAW;
		}

	};

	@Override
	public JsonElement serialize( final SourceState< ?, ? > src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final JsonObject state = new JsonObject();
		final StateType type = StateType.fromState( src );
		state.add( TYPE_KEY, context.serialize( type ) );
		switch ( type )
		{
		case RAW:
			state.add( STATE_KEY, serializeRaw( src, context ) );
			break;
		case LABEL:
			state.add( STATE_KEY, serializeLabel( ( LabelSourceState< ?, ? > ) src, context ) );
			break;
		default:
			break;
		}
		return context.serialize( state );
	}

	private static JsonObject serialize( final SourceState< ?, ? > src, final JsonSerializationContext context )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		src.dataSource().getSourceTransform( 0, 0, transform );

		final JsonObject metaData = new JsonObject();
		metaData.addProperty( META_DATA_CLASS_KEY, src.getMetaData().getClass().getName() );
		metaData.add( META_DATA_DATA_KEY, context.serialize( src.getMetaData() ) );

		final JsonObject map = new JsonObject();
		map.add( IS_VISIBLE_KEY, new JsonPrimitive( src.isVisibleProperty().get() ) );
		map.add( COMPOSITE_KEY, context.serialize( src.compositeProperty().getValue() ) );
		map.add( META_KEY, metaData );
		map.add( NAME_KEY, new JsonPrimitive( src.nameProperty().get() ) );
		map.add( INTERPOLATION_KEY, context.serialize( src.interpolationProperty().get() ) );
		map.add( TRANSFORM_KEY, context.serialize( transform ) );

		return map;
	}

	private static JsonObject serializeRaw( final SourceState< ?, ? > src, final JsonSerializationContext context )
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

	private static < D extends NativeType< D > & RealType< D >, T extends Volatile< D > & RealType< T > & NativeType< T > > SourceState< D, T > rawFromMap(
			final JsonObject map,
			final SharedQueue queue,
			final int priority,
			final Gson gson,
			final SourceState< ?, ? >... dependencies ) throws SourceCreationFailed, JsonSyntaxException, ClassNotFoundException
	{

		LOG.warn( "Getting raw from {}", map );
		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.debug( "got meta data: {}", metaData );
		final Meta meta = ( Meta ) gson.fromJson( metaData.get( META_DATA_DATA_KEY ), Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = gson.fromJson( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		@SuppressWarnings( "unchecked" )
		final Composite< ARGBType, ARGBType > composite = gson.fromJson( map.get( COMPOSITE_KEY ), Composite.class );

		final DataSource< D, T > source = meta.asSource(
				queue,
				priority,
				i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
				transform,
				name,
				dependencies );

		final ARGBColorConverter< T > converter = new ARGBColorConverter.InvertingImp1<>( source.getType().getMinValue(), source.getType().getMaxValue() );
		if ( map.has( CONVERTER_KEY ) )
		{
			final JsonObject converterSettings = map.get( CONVERTER_KEY ).getAsJsonObject();
			Optional.ofNullable( converterSettings.get( CONVERTER_MIN_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMin );
			Optional.ofNullable( converterSettings.get( CONVERTER_MAX_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMax );
			Optional.ofNullable( converterSettings.get( CONVERTER_COLOR_KEY ) ).map( JsonElement::getAsInt ).map( ARGBType::new ).ifPresent( converter::setColor );
			Optional.ofNullable( converterSettings.get( CONVERTER_ALPHA_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter.alphaProperty()::set );
		}

		final SourceState< D, T > state = new SourceState<>( source, converter, composite, name, meta );
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
			final SourceState< ?, ? >... dependencies ) throws IOException, JsonParseException, ClassNotFoundException, MetaException
	{
		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.debug( "got meta data: {}", metaData );
		final LabelMeta meta = ( LabelMeta ) gson.fromJson( metaData.get( META_DATA_DATA_KEY ), Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = gson.fromJson( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		@SuppressWarnings( "unchecked" )
		final Composite< ARGBType, ARGBType > composite = gson.fromJson( map.get( COMPOSITE_KEY ), Composite.class );

		final DataSource< LabelMultisetType, VolatileLabelMultisetType > source = ( ( Meta ) meta ).asSource(
				queue,
				priority,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				transform,
				name,
				dependencies );

		final SelectedIds selectedIds = Optional
				.ofNullable( map.get( SELECTED_IDS_KEY ) )
				.map( o -> gson.fromJson( o, SelectedIds.class ) )
				.orElse( new SelectedIds() );
		final TmpDirectoryCreator nextCanvasDirectory = new TmpDirectoryCreator( null, null );
		final String canvasDir = Optional.ofNullable( map.get( CANVAS_DIR_KEY ) ).map( JsonElement::getAsString ).orElseGet( nextCanvasDirectory );

		final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > commitCanvas = meta.commitCanvas( dependencies );
		final JsonObject serializedAssignment = map.get( ASSIGNMENT_KEY ).getAsJsonObject();
		final FragmentSegmentAssignmentState assignment;
		if ( serializedAssignment == null )
		{
			assignment = meta.assignment(
					gson.fromJson( serializedAssignment.get( FragmentSegmentAssignmentOnlyLocalSerializer.FRAGMENTS_KEY ), long[].class ),
					gson.fromJson( serializedAssignment.get( FragmentSegmentAssignmentOnlyLocalSerializer.SEGMENTS_KEY ), long[].class ),
					dependencies );
		}
		else
		{
			assignment = meta.assignment( dependencies );
		}

		final DataSource< LabelMultisetType, VolatileLabelMultisetType > maskedSource = Masks.mask(
				source,
				canvasDir,
				nextCanvasDirectory,
				commitCanvas,
				propagationExecutor );

		final InterruptibleFunction< Long, Interval[] >[] blockListCache = PainteraBaseView.generateLabelBlocksForLabelCache(
				maskedSource,
				PainteraBaseView.scaleFactorsFromAffineTransforms( maskedSource ) );

		final LongFunction< Converter< LabelMultisetType, BoolType > > getMaskGenerator = PainteraBaseView.equalMaskForLabelMultisetType();

		final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache = CacheUtils.meshCacheLoaders( maskedSource, getMaskGenerator, CacheUtils::toCacheSoftRefLoaderCache );

		final SelectedSegments activeSegments = new SelectedSegments( selectedIds, assignment );

		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( activeSegments, assignment );

		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );

		final HighlightingStreamConverterLabelMultisetType converter = new HighlightingStreamConverterLabelMultisetType( stream );
		if ( map.has( CONVERTER_KEY ) )
		{
			Optional.ofNullable( map.get( CONVERTER_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.alphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.activeFragmentAlphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY ) ).map( JsonElement::getAsInt ).ifPresent( converter.activeSegmentAlphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_SEED_KEY ) ).map( JsonElement::getAsLong ).ifPresent( converter.seedProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_COLOR_FROM_SEGMENT_KEY ) ).map( JsonElement::getAsBoolean ).ifPresent( converter.colorFromSegmentIdProperty()::set );
		}

		final MeshManagerWithAssignment meshManager = new MeshManagerWithAssignment(
				maskedSource,
				blockListCache,
				meshCache,
				root,
				assignment,
				fragmentsInSelectedSegments,
				stream,
				new SimpleIntegerProperty(),
				new SimpleDoubleProperty(),
				new SimpleIntegerProperty(),
				manager,
				workers );

		final MeshInfos meshInfos = new MeshInfos( activeSegments, assignment, meshManager, source.getNumMipmapLevels() );

		final LabelSourceState< LabelMultisetType, VolatileLabelMultisetType > state = new LabelSourceState<>(
				maskedSource,
				converter,
				composite,
				name,
				meta,
				getMaskGenerator,
				assignment,
				new ToIdConverter.FromLabelMultisetType(),
				selectedIds,
				meta.idService( dependencies ),
				meshManager,
				meshInfos );
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
			final SourceState< ?, ? >... dependencies ) throws IncompatibleTypeException, JsonParseException, ClassNotFoundException, IOException, MetaException
	{
		final StateType type = gson.fromJson( serializedSource.get( TYPE_KEY ), StateType.class );
		switch ( type )
		{
		case RAW:
			return ( SourceState ) rawFromMap(
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
			final Gson gson ) throws ClassNotFoundException, UndefinedDependency, HasCyclicDependencies, IncompatibleTypeException, JsonParseException, IOException, MetaException
	{
		final Meta[] meta = new Meta[ serializedStates.size() ];
		LOG.warn( "Deserializing {}", serializedStates );
		for ( int i = 0; i < meta.length; ++i )
		{
			final JsonObject map = serializedStates.get( i ).getAsJsonObject().get( STATE_KEY ).getAsJsonObject();
			LOG.warn( "Deserializing state {}: {}", i, map );
			final JsonElement metaTest = map.get( META_KEY );
			LOG.warn( "Meta test: {}", metaTest );
			final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
			LOG.debug( "got meta data: {}", metaData );
			meta[ i ] = ( Meta ) gson.fromJson( metaData.get( META_DATA_DATA_KEY ), Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
		}

		final HashMap< Integer, TIntHashSet > nodeEdgeMap = new HashMap<>();
		for ( int i = 0; i < meta.length; ++i )
		{
			final Meta m = meta[ i ];
			final Meta[] dependsOn = m.dependsOn();
			final int[] dependsOnIndices = new int[ dependsOn.length ];
			for ( int k = 0; k < dependsOn.length; ++k )
			{
				final int index = Arrays.asList( meta ).indexOf( dependsOn[ k ] );
				if ( index < 0 ) { throw new UndefinedDependency( m, dependsOn[ k ], meta ); }
				dependsOnIndices[ k ] = index;
			}
			nodeEdgeMap.put( i, new TIntHashSet( dependsOnIndices ) );
		}

		if ( hasCycles( nodeEdgeMap ) ) { throw new HasCyclicDependencies( meta ); }

		final SourceState< ?, ? >[] sourceStates = new SourceState[ meta.length ];

		for ( int i = 0; i < meta.length && Arrays.stream( sourceStates ).filter( s -> s == null ).count() > 0; ++i )
		{
			for ( int k = 0; k < meta.length; ++k )
			{
				if ( sourceStates[ k ] == null )
				{
					final SourceState< ?, ? >[] dependencies = IntStream.of( nodeEdgeMap.get( k ).toArray() ).mapToObj( m -> meta[ m ] ).toArray( SourceState[]::new );
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
								gson, dependencies );
					}
				}
			}
		}

		if ( Arrays.stream( sourceStates ).filter( s -> s == null ).count() > 0 )
		{
			// TODO throw appropriate Exception
		}

		return sourceStates;

	}

	private static boolean hasCycles( final HashMap< Integer, TIntHashSet > nodeEdgeMap )
	{
		final TIntHashSet visitedNodes = new TIntHashSet();
		for ( final int node : nodeEdgeMap.keySet() )
		{
			visit( nodeEdgeMap, node, visitedNodes );
		}
		return false;
	}

	private static boolean visit(
			final HashMap< Integer, TIntHashSet > nodeEdgeMap,
			final int node,
			final TIntHashSet hasVisited )
	{
		if ( hasVisited.contains( node ) ) { return true; }
		hasVisited.add( node );
		for ( final TIntIterator it = nodeEdgeMap.get( node ).iterator(); it.hasNext(); )
		{
			final boolean foundAlreadyVisitedNode = visit( nodeEdgeMap, it.next(), hasVisited );
			if ( foundAlreadyVisitedNode ) { return foundAlreadyVisitedNode; }
		}
		return false;
	}

}
