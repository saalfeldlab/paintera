package org.janelia.saalfeldlab.paintera.serialization;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentsInSelectedSegments;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.mask.TmpDirectoryCreator;
import org.janelia.saalfeldlab.paintera.id.ToIdConverter;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManagerWithAssignment;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.n5.N5FSMeta;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterLabelMultisetType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Group;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValueTriple;

public class SourceStateSerializer implements JsonDeserializer< SourceState< ?, ? > >, JsonSerializer< SourceState< ?, ? > >
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

	private final ExecutorService propagationExecutor;

	private final ExecutorService manager;

	private final ExecutorService workers;

	private final SharedQueue queue;

	private final int priority;

	private final Group root;

	public SourceStateSerializer(
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final SharedQueue queue,
			final int priority,
			final Group root )
	{
		super();
		this.propagationExecutor = propagationExecutor;
		this.manager = manager;
		this.workers = workers;
		this.queue = queue;
		this.priority = priority;
		this.root = root;
	}

	@Override
	public SourceState< ?, ? > deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		final JsonObject map = context.deserialize( json, JsonObject.class );

		final StateType type = context.deserialize( map.get( TYPE_KEY ), StateType.class );

		final JsonObject stateMap = context.deserialize( map.get( STATE_KEY ), JsonObject.class );
		// ( Map< String, JsonElement > ) map.get( STATE_KEY );

		switch ( type )
		{
		case RAW:
			try
			{
				LOG.debug( "Returning raw!" );
				return rawFromMap( stateMap, queue, priority, context );
			}
			catch ( final IOException | ClassNotFoundException e )
			{
				throw new JsonParseException( "Failed to generate raw state from map: " + stateMap, e );
			}
		case LABEL:
			try
			{
				LOG.debug( "Returning raw!" );
				return labelFromMap( stateMap, queue, priority, root, propagationExecutor, manager, workers, context );
			}
			catch ( final IOException | ClassNotFoundException e )
			{
				throw new JsonParseException( "Failed to generate raw state from map: " + stateMap, e );
			}
		default:
			break;
		}

		return null;
	}

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

	private static < D extends NativeType< D > & RealType< D >, T extends Volatile< D > & RealType< T > > SourceState< D, T > rawFromMap(
			final JsonObject map,
			final SharedQueue queue,
			final int priority,
			final JsonDeserializationContext context ) throws IOException, JsonParseException, ClassNotFoundException
	{

		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.debug( "got meta data: {}", metaData );
		final Object meta = context.deserialize( metaData.get( META_DATA_DATA_KEY ), Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = ( AffineTransform3D ) context.deserialize( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		final Composite< ARGBType, ARGBType > composite = context.deserialize( map.get( COMPOSITE_KEY ), Composite.class );

		final N5Reader n5;
		final String dataset;
		if ( meta instanceof N5FSMeta )
		{
			final N5FSMeta n5meta = ( N5FSMeta ) meta;
			n5 = new N5FSReader( n5meta.root );
			dataset = n5meta.dataset;
		}
		else
		{
			n5 = null;
			dataset = null;
		}

		final boolean isMultiScale = N5Helpers.isMultiScale( n5, dataset );
		final RandomAccessibleIntervalDataSource< D, T > source;
		if ( isMultiScale )
		{
			final ValueTriple< RandomAccessibleInterval< D >[], RandomAccessibleInterval< T >[], AffineTransform3D[] > data = N5Helpers.openRawMultiscale(
					n5,
					dataset,
					transform,
					queue,
					priority );
			source = new RandomAccessibleIntervalDataSource<>(
					data.getA(),
					data.getB(),
					data.getC(),
					i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
					i -> Interpolation.NLINEAR.equals( i ) ? new NLinearInterpolatorFactory<>() : new NearestNeighborInterpolatorFactory<>(),
					name );
		}
		else
		{
			source = DataSource.createN5Source( name, n5, dataset, transform, queue, priority );
		}

		final DatasetAttributes attributes = n5.getDatasetAttributes(
				N5Helpers.isMultiScale( n5, dataset )
						? Paths.get( dataset, N5Helpers.listAndSortScaleDatasets( n5, dataset )[ 0 ] ).toString()
						: dataset );
		final DataType type = attributes.getDataType();

		final ARGBColorConverter< T > converter = new ARGBColorConverter.InvertingImp1<>( N5Helpers.minForType( type ), N5Helpers.maxForType( type ) );
		if ( map.has( CONVERTER_KEY ) )
		{
			final JsonObject converterSettings = map.get( CONVERTER_KEY ).getAsJsonObject();
			Optional.ofNullable( converterSettings.get( CONVERTER_MIN_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMin );
			Optional.ofNullable( converterSettings.get( CONVERTER_MAX_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter::setMax );
			Optional.ofNullable( converterSettings.get( CONVERTER_COLOR_KEY ) ).map( JsonElement::getAsInt ).map( ARGBType::new ).ifPresent( converter::setColor );
			Optional.ofNullable( converterSettings.get( CONVERTER_ALPHA_KEY ) ).map( JsonElement::getAsDouble ).ifPresent( converter.alphaProperty()::set );
		}

		return new SourceState<>( source, converter, composite, name, meta );
	}

	private static LabelSourceState< LabelMultisetType, VolatileLabelMultisetType > labelFromMap(
			final JsonObject map,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers,
			final JsonDeserializationContext context ) throws IOException, JsonParseException, ClassNotFoundException
	{
		final JsonObject metaData = map.get( META_KEY ).getAsJsonObject();
		LOG.debug( "got meta data: {}", metaData );
		final Object meta = context.deserialize( metaData.get( META_DATA_DATA_KEY ), Class.forName( metaData.get( META_DATA_CLASS_KEY ).getAsString() ) );
		final String name = map.get( NAME_KEY ).getAsString();
		LOG.debug( "Got name={}", name );
		final AffineTransform3D transform = ( AffineTransform3D ) context.deserialize( map.get( TRANSFORM_KEY ), AffineTransform3D.class );
		final Composite< ARGBType, ARGBType > composite = context.deserialize( map.get( COMPOSITE_KEY ), Composite.class );

		final N5Writer n5;
		final String dataset;
		if ( meta instanceof N5FSMeta )
		{
			final N5FSMeta n5meta = ( N5FSMeta ) meta;
			n5 = new N5FSWriter( n5meta.root );
			dataset = n5meta.dataset;
		}
		else
		{
			n5 = null;
			dataset = null;
		}

		final boolean isMultiScale = N5Helpers.isMultiScale( n5, dataset );
		final ValueTriple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] > data =
				isMultiScale
						? N5Helpers.openLabelMultisetMultiscale( n5, dataset, transform, queue, priority )
						: N5Helpers.asArrayTriple( N5Helpers.openLabelMutliset( n5, dataset, transform, queue, priority ) );

		final RandomAccessibleIntervalDataSource< LabelMultisetType, VolatileLabelMultisetType > source = new RandomAccessibleIntervalDataSource<>(
				data.getA(),
				data.getB(),
				data.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name );

		final DatasetAttributes attributes = n5.getDatasetAttributes(
				N5Helpers.isMultiScale( n5, dataset )
						? Paths.get( dataset, N5Helpers.listAndSortScaleDatasets( n5, dataset )[ 0 ] ).toString()
						: dataset );

		final SelectedIds selectedIds = Optional
				.ofNullable( map.get( SELECTED_IDS_KEY ) )
				.map( o -> ( SelectedIds ) context.deserialize( o, SelectedIds.class ) )
				.orElse( new SelectedIds() );
		final TmpDirectoryCreator nextCanvasDirectory = new TmpDirectoryCreator( null, null );
		final String canvasDir = Optional.ofNullable( map.get( CANVAS_DIR_KEY ) ).map( JsonElement::getAsString ).orElseGet( nextCanvasDirectory );
		final CommitCanvasN5 commitCanvas = new CommitCanvasN5( n5, dataset );
		final FragmentSegmentAssignmentOnlyLocal deserializedAssignment = context.deserialize(
				map.get( ASSIGNMENT_KEY ),
				FragmentSegmentAssignmentOnlyLocal.class );
		final int size = deserializedAssignment.size();
		final long[] fragments = new long[ size ];
		final long[] segments = new long[ size ];
		deserializedAssignment.persist( fragments, segments );

		final DataSource< LabelMultisetType, VolatileLabelMultisetType > maskedSource = Masks.mask(
				source,
				canvasDir,
				nextCanvasDirectory,
				commitCanvas,
				propagationExecutor );
//		masked = Masks.from

		final InterruptibleFunction< Long, Interval[] >[] blockListCache = PainteraBaseView.generateLabelBlocksForLabelCache(
				maskedSource,
				PainteraBaseView.scaleFactorsFromAffineTransforms( maskedSource ) );

		final LongFunction< Converter< LabelMultisetType, BoolType > > getMaskGenerator = PainteraBaseView.equalMaskForLabelMultisetType();

		final InterruptibleFunction< ShapeKey, Pair< float[], float[] > >[] meshCache = CacheUtils.meshCacheLoaders( maskedSource, getMaskGenerator, CacheUtils::toCacheSoftRefLoaderCache );

		final FragmentSegmentAssignmentState assignment = N5Helpers.assignments( n5, dataset, fragments, segments );

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

		return new LabelSourceState<>(
				maskedSource,
				converter,
				composite,
				name,
				meta,
				getMaskGenerator,
				assignment,
				new ToIdConverter.FromLabelMultisetType(),
				selectedIds,
				N5Helpers.idService( n5, dataset ),
				meshManager,
				meshInfos );

	}

}
