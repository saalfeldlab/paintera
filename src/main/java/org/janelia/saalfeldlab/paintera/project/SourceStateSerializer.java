package org.janelia.saalfeldlab.paintera.project;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.LongFunction;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
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
import org.janelia.saalfeldlab.paintera.n5.N5HDF5Meta;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverterLabelMultisetType;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
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
import net.imglib2.converter.ARGBColorConverter.Imp1;
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

public class SourceStateSerializer implements JsonSerializer< SourceState< ?, ? > >, JsonDeserializer< SourceState< ?, ? > >
{

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

	private static final String CONVERTER_MAX_KEY = "min";

	private static final String CONVERTER_COLOR_KEY = "color";

	private static final String CONVERTER_ALPHA_KEY = "alpha";

	private static final String CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY = "alpha";

	private static final String CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY = "alpha";

	private static final String CONVERTER_SEED_KEY = "seed";

	private static final String CONVERTER_COLOR_FROM_SEGMENT_KEY = "colorFromSegment";

	private static final String CANVAS_DIR_KEY = "canvasDir";

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

	public SourceStateSerializer(
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers )
	{
		super();
		this.propagationExecutor = propagationExecutor;
		this.manager = manager;
		this.workers = workers;
	}

	@Override
	public SourceState< ?, ? > deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
	{
		final Map< String, Object > map = context.deserialize( json, Map.class );

		final StateType type = ( StateType ) map.get( TYPE_KEY );

		final Map< String, Object > stateMap = ( Map< String, Object > ) map.get( STATE_KEY );

		switch ( type )
		{
		case RAW:
			break;
		case LABEL:
			break;
		default:
			break;
		}

		return null;
	}

	@Override
	public JsonElement serialize( final SourceState< ?, ? > src, final Type typeOfSrc, final JsonSerializationContext context )
	{
		final Map< String, Object > state = new HashMap<>();
		final StateType type = StateType.fromState( src );
		state.put( TYPE_KEY, type );
		switch ( type )
		{
		case RAW:
			state.put( STATE_KEY, serializeRaw( src ) );
			break;
		case LABEL:
			state.put( STATE_KEY, serializeLabel( ( LabelSourceState< ?, ? > ) src ) );
			break;
		default:
			break;
		}

		return context.serialize( state );
	}

	private static Map< String, Object > serialize( final SourceState< ?, ? > src )
	{
		final AffineTransform3D transform = new AffineTransform3D();
		src.dataSource().getSourceTransform( 0, 0, transform );

		final Map< String, Object > map = new HashMap<>();
		map.put( IS_VISIBLE_KEY, src.isVisibleProperty() );
		map.put( COMPOSITE_KEY, src.compositeProperty().getValue() );
		map.put( META_KEY, src.getMetaData() );
		map.put( NAME_KEY, src.nameProperty().getValue() );
		map.put( INTERPOLATION_KEY, src.interpolationProperty().get() );
		map.put( TRANSFORM_KEY, transform );

		return map;
	}

	private static Map< String, Object > serializeRaw( final SourceState< ?, ? > src )
	{
		final Map< String, Object > map = serialize( src );

		final Converter< ?, ARGBType > converter = src.getConverter();
		if ( converter instanceof ARGBColorConverter< ? > )
		{
			final ARGBColorConverter< ? > c = ( ARGBColorConverter< ? > ) converter;
			final Map< String, Object > converterMap = new HashMap<>();
			converterMap.put( CONVERTER_MIN_KEY, c.getMin() );
			converterMap.put( CONVERTER_MAX_KEY, c.getMax() );
			converterMap.put( CONVERTER_ALPHA_KEY, c.alphaProperty().get() );
			converterMap.put( CONVERTER_COLOR_KEY, c.getColor().get() );
			map.put( CONVERTER_KEY, converterMap );
		}

		return map;
	}

	private static Map< String, Object > serializeLabel( final LabelSourceState< ?, ? > src )
	{
		final Map< String, Object > map = serialize( src );
		map.put( SELECTED_IDS_KEY, src.selectedIds() );
		// TODO do assignment
//		map.put( ASSIGNMENT_KEY, src.assignment() );

		final Converter< ?, ARGBType > converter = src.getConverter();
		if ( converter instanceof HighlightingStreamConverter< ? > )
		{
			final HighlightingStreamConverter< ? > c = ( HighlightingStreamConverter< ? > ) converter;
			final Map< String, Object > converterMap = new HashMap<>();
			map.put( CONVERTER_ALPHA_KEY, c.alphaProperty().get() );
			map.put( CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY, c.activeFragmentAlphaProperty().get() );
			map.put( CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY, c.activeSegmentAlphaProperty().get() );
			map.put( CONVERTER_SEED_KEY, c.seedProperty().get() );
			map.put( CONVERTER_COLOR_FROM_SEGMENT_KEY, c.colorFromSegmentIdProperty().get() );
			map.put( CONVERTER_KEY, converterMap );
		}

		return map;
	}

	private static < D extends NativeType< D > & RealType< D >, T extends Volatile< D > & RealType< T > > SourceState< D, T > rawFromMap(
			final Map< String, Object > map,
			final SharedQueue queue,
			final int priority ) throws IOException
	{
		final Object meta = map.get( META_KEY );
		final String name = ( String ) map.get( NAME_KEY );
		final AffineTransform3D transform = ( AffineTransform3D ) map.get( TRANSFORM_KEY );
		final Composite< ARGBType, ARGBType > composite = ( Composite< ARGBType, ARGBType > ) map.get( COMPOSITE_KEY );

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

		final Imp1< T > converter = new ARGBColorConverter.Imp1<>( N5Helpers.minForType( type ), N5Helpers.maxForType( type ) );
		if ( map.containsKey( CONVERTER_KEY ) )
		{
			final Map< String, Object > converterSettings = ( Map< String, Object > ) map.get( CONVERTER_KEY );
			Optional.ofNullable( converterSettings.get( CONVERTER_MIN_KEY ) ).map( o -> ( Double ) o ).ifPresent( converter::setMin );
			Optional.ofNullable( converterSettings.get( CONVERTER_MAX_KEY ) ).map( o -> ( Double ) o ).ifPresent( converter::setMax );
			Optional.ofNullable( converterSettings.get( CONVERTER_COLOR_KEY ) ).map( o -> new ARGBType( ( int ) o ) ).ifPresent( converter::setColor );
			Optional.ofNullable( converterSettings.get( CONVERTER_ALPHA_KEY ) ).map( o -> ( Double ) o ).ifPresent( converter.alphaProperty()::set );
		}

		return new SourceState<>( source, converter, composite, name, meta );
	}

	private static LabelSourceState< LabelMultisetType, VolatileLabelMultisetType > labelFromMap(
			final Map< String, Object > map,
			final SharedQueue queue,
			final int priority,
			final Group root,
			final ExecutorService propagationExecutor,
			final ExecutorService manager,
			final ExecutorService workers ) throws IOException
	{
		final Object meta = map.get( META_KEY );
		final String name = ( String ) map.get( NAME_KEY );
		final AffineTransform3D transform = ( AffineTransform3D ) map.get( TRANSFORM_KEY );
		final Composite< ARGBType, ARGBType > composite = ( Composite< ARGBType, ARGBType > ) map.get( COMPOSITE_KEY );

		final N5Writer n5;
		final String dataset;
		if ( meta instanceof N5FSMeta )
		{
			final N5FSMeta n5meta = ( N5FSMeta ) meta;
			n5 = new N5FSWriter( n5meta.root );
			dataset = n5meta.dataset;
		}
		else if ( meta instanceof N5HDF5Meta )
		{
			final N5HDF5Meta h5meta = ( N5HDF5Meta ) meta;
			n5 = new N5HDF5Writer( h5meta.h5file, h5meta.defaultBlockSize );
			dataset = h5meta.dataset;
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
		final DataType type = attributes.getDataType();

		final SelectedIds selectedIds = ( SelectedIds ) map.computeIfAbsent( SELECTED_IDS_KEY, k -> new SelectedIds() );
		final FragmentSegmentAssignmentState assignments = N5Helpers.assignments( n5, dataset );
		final TmpDirectoryCreator nextCanvasDirectory = new TmpDirectoryCreator( null, null );
		final String canvasDir = ( String ) map.computeIfAbsent( CANVAS_DIR_KEY, k -> nextCanvasDirectory.get() );
		final CommitCanvasN5 commitCanvas = new CommitCanvasN5( n5, dataset );

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

		final FragmentSegmentAssignmentState assignment = N5Helpers.assignments( n5, dataset );

		final SelectedSegments activeSegments = new SelectedSegments( selectedIds, assignment );

		final FragmentsInSelectedSegments fragmentsInSelectedSegments = new FragmentsInSelectedSegments( activeSegments, assignment );

		final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new ModalGoldenAngleSaturatedHighlightingARGBStream( selectedIds, assignment );

		final HighlightingStreamConverterLabelMultisetType converter = new HighlightingStreamConverterLabelMultisetType( stream );
		if ( map.containsKey( CONVERTER_KEY ) )
		{
			Optional.ofNullable( map.get( CONVERTER_ALPHA_KEY ) ).map( o -> ( Integer ) o ).ifPresent( converter.alphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_ACTIVE_FRAGMENT_ALPHA_KEY ) ).map( o -> ( Integer ) o ).ifPresent( converter.activeFragmentAlphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_ACTIVE_SEGMENT_ALPHA_KEY ) ).map( o -> ( Integer ) o ).ifPresent( converter.activeSegmentAlphaProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_SEED_KEY ) ).map( o -> ( Long ) o ).ifPresent( converter.seedProperty()::set );
			Optional.ofNullable( map.get( CONVERTER_COLOR_FROM_SEGMENT_KEY ) ).map( o -> ( Boolean ) o ).ifPresent( converter.colorFromSegmentIdProperty()::set );
		}

		final MeshManagerWithAssignment meshManager = new MeshManagerWithAssignment(
				maskedSource,
				blockListCache,
				meshCache,
				null,
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
