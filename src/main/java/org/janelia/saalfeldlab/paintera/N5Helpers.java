package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.LongArrayDataBlock;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.paintera.ui.opendialog.VolatileHelpers;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.janelia.saalfeldlab.util.MakeUnchecked.CheckedConsumer;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileRandomAccessibleIntervalView;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.ref.BoundedSoftRefLoaderCache;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.util.LoaderCacheAsCacheAdapter;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.N5CacheLoader;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.ValueTriple;
import net.imglib2.view.Views;

public class N5Helpers
{

	public static final String MULTI_SCALE_KEY = "multiScale";

	public static final String IS_LABEL_MULTISET_KEY = "isLabelMultiset";

	public static final String MAX_ID_KEY = "maxId";

	public static final String RESOLUTION_KEY = "resolution";

	public static final String OFFSET_KEY = "offset";

	public static final String DOWNSAMPLING_FACTORS_KEY = "downsamplingFactors";

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static boolean isIntegerType( final DataType type )
	{
		switch ( type )
		{
		case INT8:
		case INT16:
		case INT32:
		case INT64:
		case UINT8:
		case UINT16:
		case UINT32:
		case UINT64:
			return true;
		default:
			return false;
		}
	}

	public static boolean isMultiScale( final N5Reader n5, final String dataset ) throws IOException
	{
		/* based on attribute */
		boolean isMultiScale = Optional.ofNullable( n5.getAttribute( dataset, MULTI_SCALE_KEY, Boolean.class ) ).orElse( false );

		/*
		 * based on groupd content (the old way) TODO conider removing as
		 * multi-scale declaration by attribute becomes part of the N5 spec.
		 */
		if ( !isMultiScale && !n5.datasetExists( dataset ) )
		{
			final String[] groups = n5.list( dataset );
			isMultiScale = groups.length > 0;
			for ( final String group : groups )
				if ( !( group.matches( "^s[0-9]+$" ) && n5.datasetExists( dataset + "/" + group ) ) )
				{
					isMultiScale = false;
					break;
				}
			if ( isMultiScale )
			{
				LOG.warn(
						"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in the future. Please add \"{}\":{} to attributes.json.",
						MULTI_SCALE_KEY,
						MULTI_SCALE_KEY,
						true );
			}
		}
		return isMultiScale;
	}

	public static boolean isLabelMultisetType( final N5Reader n5, final String dataset, final boolean isMultiscale ) throws IOException
	{
		return isMultiscale && isLabelMultisetType( n5, getFinestLevel( n5, dataset ) )
				|| Optional.ofNullable( n5.getAttribute( dataset, IS_LABEL_MULTISET_KEY, Boolean.class ) ).orElse( false );
	}

	public static boolean isLabelMultisetType( final N5Reader n5, final String dataset ) throws IOException
	{
		return Optional.ofNullable( n5.getAttribute( dataset, IS_LABEL_MULTISET_KEY, Boolean.class ) ).orElse( false );
	}

	public static double minForType( final DataType t )
	{
		// TODO ever return non-zero here?
		switch ( t )
		{
		default:
			return 0.0;
		}
	}

	public static double maxForType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return 0xff;
		case UINT16:
			return 0xffff;
		case UINT32:
			return 0xffffffffl;
		case UINT64:
			return 2.0 * Long.MAX_VALUE;
		case INT8:
			return Byte.MAX_VALUE;
		case INT16:
			return Short.MAX_VALUE;
		case INT32:
			return Integer.MAX_VALUE;
		case INT64:
			return Long.MAX_VALUE;
		case FLOAT32:
		case FLOAT64:
			return 1.0;
		default:
			return 1.0;
		}
	}

	public static String[] listScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		final String[] scaleDirs = Arrays
				.stream( n5.list( group ) )
				.filter( s -> s.matches( "^s\\d+$" ) )
				.filter( s -> {
					try
					{
						return n5.datasetExists( group + "/" + s );
					}
					catch ( final IOException e )
					{
						return false;
					}
				} )
				.toArray( String[]::new );

		LOG.debug( "Found these scale dirs: {}", Arrays.toString( scaleDirs ) );
		return scaleDirs;
	}

	public static String[] listAndSortScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		final String[] scaleDirs = listScaleDatasets( n5, group );
		sortScaleDatasets( scaleDirs );

		LOG.debug( "Sorted scale dirs: {}", Arrays.toString( scaleDirs ) );
		return scaleDirs;
	}

	public static void sortScaleDatasets( final String[] scaleDatasets )
	{
		Arrays.sort( scaleDatasets, ( f1, f2 ) -> {
			return Integer.compare(
					Integer.parseInt( f1.replaceAll( "[^\\d]", "" ) ),
					Integer.parseInt( f2.replaceAll( "[^\\d]", "" ) ) );
		} );
	}

	public static N5Reader n5Reader( final String base, final int... defaultCellDimensions ) throws IOException
	{
		return isHDF( base ) ? new N5HDF5Reader( base, defaultCellDimensions ) : new N5FSReader( base );
	}

	public static N5Writer n5Writer( final String base, final int... defaultCellDimensions ) throws IOException
	{
		return isHDF( base ) ? new N5HDF5Writer( base, defaultCellDimensions ) : new N5FSWriter( base );
	}

	public static boolean isHDF( final String base )
	{
		LOG.debug( "Checking {} for HDF", base );
		final boolean isHDF = Pattern.matches( "^h5://", base ) || Pattern.matches( "^.*\\.(hdf|h5)$", base );
		LOG.debug( "{} is hdf5? {}", base, isHDF );
		return isHDF;
	}

	public static List< String > discoverDatasets( final N5Reader n5, final Runnable onInterruption )
	{
		final List< String > datasets = new ArrayList<>();
		final ExecutorService exec = Executors.newFixedThreadPool( n5 instanceof N5HDF5Reader ? 1 : 12, new NamedThreadFactory( "dataset-discovery-%d", true ) );
		final AtomicInteger counter = new AtomicInteger( 1 );
		exec.submit( () -> discoverSubdirectories( n5, "", datasets, exec, counter ) );
		while ( counter.get() > 0 && !Thread.currentThread().isInterrupted() )
			try
			{
				Thread.sleep( 20 );
			}
			catch ( final InterruptedException e )
			{
				exec.shutdownNow();
				onInterruption.run();
			}
		exec.shutdown();
		Collections.sort( datasets );
		return datasets;
	}

	public static void discoverSubdirectories(
			final N5Reader n5,
			final String pathName,
			final Collection< String > datasets,
			final ExecutorService exec,
			final AtomicInteger counter )
	{
		try
		{
			if ( n5.datasetExists( pathName ) )
			{
				synchronized ( datasets )
				{
					datasets.add( pathName );
				}
			}
			else
			{
				String[] groups = null;
				/* based on attribute */
				boolean isMipmapGroup = Optional.ofNullable( n5.getAttribute( pathName, MULTI_SCALE_KEY, Boolean.class ) ).orElse( false );

				/* based on groupd content (the old way) */
				if ( !isMipmapGroup )
				{
					groups = n5.list( pathName );
					isMipmapGroup = groups.length > 0;
					for ( final String group : groups )
					{
						if ( !( group.matches( "^s[0-9]+$" ) && n5.datasetExists( pathName + "/" + group ) ) )
						{
							isMipmapGroup = false;
							break;
						}
					}
					if ( isMipmapGroup )
					{
						LOG.warn(
								"Found multi-scale group without {} tag. Implicit multi-scale detection will be removed in the future. Please add \"{}\":{} to attributes.json.",
								MULTI_SCALE_KEY,
								MULTI_SCALE_KEY,
								true );
					}
				}
				if ( isMipmapGroup )
				{
					synchronized ( datasets )
					{
						datasets.add( pathName );
					}
				}
				else if ( groups != null )
				{
					for ( final String group : groups )
					{
						final String groupPathName = pathName + "/" + group;
						final int numThreads = counter.incrementAndGet();
						LOG.debug( "entering {}, {} threads created", groupPathName, numThreads );
						exec.submit( () -> discoverSubdirectories( n5, groupPathName, datasets, exec, counter ) );
					}
				}
			}
		}
		catch ( final IOException e )
		{
			LOG.debug( e.toString(), e );
		}
		final int numThreads = counter.decrementAndGet();
		LOG.debug( "leaving {}, {} threads remaining", pathName, numThreads );
	}

	public static < T extends NativeType< T >, V extends Volatile< T > > ValueTriple< RandomAccessibleInterval< T >, RandomAccessibleInterval< V >, AffineTransform3D >
			openRaw(
					final N5Reader reader,
					final String dataset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		return openRaw( reader, dataset, getResolution( reader, dataset ), getOffset( reader, dataset ), sharedQueue, priority );
	}

	public static < T extends NativeType< T >, V extends Volatile< T > > ValueTriple< RandomAccessibleInterval< T >, RandomAccessibleInterval< V >, AffineTransform3D >
			openRaw(
					final N5Reader reader,
					final String dataset,
					final double[] resolution,
					final double[] offset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
		final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		return new ValueTriple<>( raw, vraw, transform );
	}

	public static < T extends NativeType< T >, V extends Volatile< T > > ValueTriple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] >
			openRawMultiscale(
					final N5Reader reader,
					final String dataset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		return openRawMultiscale(
				reader,
				dataset,
				getResolution( reader, dataset ),
				getOffset( reader, dataset ),
				sharedQueue,
				priority );

	}

	public static < T extends NativeType< T >, V extends Volatile< T > > ValueTriple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] >
			openRawMultiscale(
					final N5Reader reader,
					final String dataset,
					final double[] resolution,
					final double[] offset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets( reader, dataset );

		LOG.debug( "Opening directories {} as multi-scale in {}: ", Arrays.toString( scaleDatasets ), dataset );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< T >[] raw = new RandomAccessibleInterval[ scaleDatasets.length ];
		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< V >[] vraw = new RandomAccessibleInterval[ scaleDatasets.length ];
		final AffineTransform3D[] transforms = new AffineTransform3D[ scaleDatasets.length ];
		final double[] initialDonwsamplingFactors = getDownsamplingFactors( reader, Paths.get( dataset, scaleDatasets[ 0 ] ).toString() );
		LOG.debug( "Initial resolution={}", Arrays.toString( resolution ) );
		final ExecutorService es = Executors.newFixedThreadPool( scaleDatasets.length, new NamedThreadFactory( "populate-mipmap-scales-%d", true ) );
		final ArrayList< Future< Boolean > > futures = new ArrayList<>();
		for ( int scale = 0; scale < scaleDatasets.length; ++scale )
		{
			final int fScale = scale;
			futures.add( es.submit( MakeUnchecked.unchecked( () -> {
				LOG.debug( "Populating scale level {}", fScale );
				final String scaleDataset = Paths.get( dataset, scaleDatasets[ fScale ] ).toString();
				final ValueTriple< RandomAccessibleInterval< T >, RandomAccessibleInterval< V >, AffineTransform3D > cachedAndVolatile =
						openRaw( reader, scaleDataset, resolution, offset, sharedQueue, priority );
				raw[ fScale ] = cachedAndVolatile.getA();
				vraw[ fScale ] = cachedAndVolatile.getB();
				final double[] downsamplingFactors = getDownsamplingFactors( reader, scaleDataset );
				LOG.debug( "Read downsampling factors: {}", Arrays.toString( downsamplingFactors ) );
				transforms[ fScale ] = considerDownsampling( cachedAndVolatile.getC(), downsamplingFactors, initialDonwsamplingFactors );
				LOG.debug( "Populated scale level {}", fScale );
				return true;
			} )::get ) );
		}
		futures.forEach( MakeUnchecked.unchecked( ( CheckedConsumer< Future< Boolean > > ) Future::get ) );
		es.shutdown();
		return new ValueTriple<>( raw, vraw, transforms );
	}

	public static ValueTriple< RandomAccessibleInterval< LabelMultisetType >, RandomAccessibleInterval< VolatileLabelMultisetType >, AffineTransform3D >
			openLabelMutliset(
					final N5Reader reader,
					final String dataset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		return openLabelMutliset( reader, dataset, getResolution( reader, dataset ), getOffset( reader, dataset ), sharedQueue, priority );
	}

	public static ValueTriple< RandomAccessibleInterval< LabelMultisetType >, RandomAccessibleInterval< VolatileLabelMultisetType >, AffineTransform3D >

			openLabelMutliset(
					final N5Reader reader,
					final String dataset,
					final double[] resolution,
					final double[] offset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		final DatasetAttributes attrs = reader.getDatasetAttributes( dataset );
		final N5CacheLoader loader = new N5CacheLoader( reader, dataset );
		final SoftRefLoaderCache< Long, Cell< VolatileLabelMultisetArray > > cache = new SoftRefLoaderCache<>();
		final LoaderCacheAsCacheAdapter< Long, Cell< VolatileLabelMultisetArray > > wrappedCache = new LoaderCacheAsCacheAdapter<>( cache, loader );
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > cachedImg = new CachedCellImg<>(
				new CellGrid( attrs.getDimensions(), attrs.getBlockSize() ),
				new LabelMultisetType(),
				wrappedCache,
				new VolatileLabelMultisetArray( 0, true ) );
		final VolatileRandomAccessibleIntervalView< LabelMultisetType, VolatileLabelMultisetType > volatileCachedImg = VolatileHelpers.wrapCachedCellImg(
				cachedImg,
				new VolatileHelpers.CreateInvalidVolatileLabelMultisetArray( cachedImg.getCellGrid() ),
				sharedQueue,
				new CacheHints( LoadingStrategy.VOLATILE, priority, false ),
				new VolatileLabelMultisetType() );

		return new ValueTriple<>( cachedImg, volatileCachedImg, fromResolutionAndOffset( resolution, offset ) );

	}

	public static ValueTriple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] >
			openLabelMultisetMultiscale(
					final N5Reader reader,
					final String dataset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		return openLabelMultisetMultiscale(
				reader,
				dataset,
				getResolution( reader, dataset ),
				getOffset( reader, dataset ),
				sharedQueue,
				priority );
	}

	public static ValueTriple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] >
			openLabelMultisetMultiscale(
					final N5Reader reader,
					final String dataset,
					final double[] resolution,
					final double[] offset,
					final SharedQueue sharedQueue,
					final int priority ) throws IOException
	{
		final String[] scaleDatasets = N5Helpers.listAndSortScaleDatasets( reader, dataset );

		LOG.debug( "Opening directories {} as multi-scale in {}: ", Arrays.toString( scaleDatasets ), dataset );

		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< LabelMultisetType >[] raw = new RandomAccessibleInterval[ scaleDatasets.length ];
		@SuppressWarnings( "unchecked" )
		final RandomAccessibleInterval< VolatileLabelMultisetType >[] vraw = new RandomAccessibleInterval[ scaleDatasets.length ];
		final AffineTransform3D[] transforms = new AffineTransform3D[ scaleDatasets.length ];
		final double[] initialDonwsamplingFactors = getDownsamplingFactors( reader, Paths.get( dataset, scaleDatasets[ 0 ] ).toString() );
		final ExecutorService es = Executors.newFixedThreadPool( scaleDatasets.length, new NamedThreadFactory( "populate-mipmap-scales-%d", true ) );
		final ArrayList< Future< Boolean > > futures = new ArrayList<>();
		for ( int scale = 0; scale < scaleDatasets.length; ++scale )
		{
			final int fScale = scale;
			futures.add( es.submit( MakeUnchecked.unchecked( () -> {
				LOG.debug( "Populating scale level {}", fScale );
				final String scaleDataset = Paths.get( dataset, scaleDatasets[ fScale ] ).toString();
				final ValueTriple< RandomAccessibleInterval< LabelMultisetType >, RandomAccessibleInterval< VolatileLabelMultisetType >, AffineTransform3D > cachedAndVolatile =
						openLabelMutliset( reader, scaleDataset, resolution, offset, sharedQueue, priority );
				raw[ fScale ] = cachedAndVolatile.getA();
				vraw[ fScale ] = cachedAndVolatile.getB();
				final double[] downsamplingFactors = getDownsamplingFactors( reader, scaleDataset );
				LOG.debug( "Read downsampling factors: {}", Arrays.toString( downsamplingFactors ) );
				transforms[ fScale ] = considerDownsampling( cachedAndVolatile.getC(), downsamplingFactors, initialDonwsamplingFactors );
				LOG.debug( "Populated scale level {}", fScale );
				return true;
			} )::get ) );
		}
		futures.forEach( MakeUnchecked.unchecked( ( CheckedConsumer< Future< Boolean > > ) Future::get ) );
		es.shutdown();
		return new ValueTriple<>( raw, vraw, transforms );
	}

	public static AffineTransform3D considerDownsampling(
			final double[] initialResolution,
			final double[] offset,
			final double[] downsamplingFactors,
			final double[] initialDownsamplingFactors )
	{
		final double[] scaledResolution = new double[ downsamplingFactors.length ];
		final double[] shift = new double[ downsamplingFactors.length ];

		for ( int d = 0; d < downsamplingFactors.length; ++d )
		{
			scaledResolution[ d ] = downsamplingFactors[ d ] * initialResolution[ d ];
			shift[ d ] = 0.5 / initialDownsamplingFactors[ d ] - 0.5 / downsamplingFactors[ d ];
		}

		LOG.debug( "Downsampling factors={}, scaled resolution={}", Arrays.toString( downsamplingFactors ), Arrays.toString( scaledResolution ) );

		final AffineTransform3D transform = new AffineTransform3D();
		transform.set(
				scaledResolution[ 0 ], 0, 0, offset[ 0 ],
				0, scaledResolution[ 1 ], 0, offset[ 1 ],
				0, 0, scaledResolution[ 2 ], offset[ 2 ] );
		return transform.concatenate( new Translation3D( shift ) );
	}

	public static AffineTransform3D considerDownsampling(
			final AffineTransform3D transform,
			final double[] downsamplingFactors,
			final double[] initialDownsamplingFactors )
	{
		final double[] shift = new double[ downsamplingFactors.length ];
		for ( int d = 0; d < downsamplingFactors.length; ++d )
		{
			transform.set( transform.get( d, d ) * downsamplingFactors[ d ] / initialDownsamplingFactors[ d ], d, d );
			shift[ d ] = 0.5 / initialDownsamplingFactors[ d ] - 0.5 / downsamplingFactors[ d ];
		}
		return transform.concatenate( new Translation3D( shift ) );
	}

	public static FragmentSegmentAssignmentState assignments( final N5Writer writer, final String ds ) throws IOException
	{
		final String dataset = ds + ".fragment-segment-assignment";

		final BiConsumer< long[], long[] > persister = ( keys, values ) -> {
			if ( keys.length == 0 )
			{
				LOG.info( "Zero length data, will not persist fragment-segment-assignment." );
				return;
			}
			try
			{
				final DatasetAttributes attrs = new DatasetAttributes( new long[] { keys.length, 2 }, new int[] { keys.length, 1 }, DataType.UINT64, new GzipCompression() );
				writer.createDataset( dataset, attrs );
				final DataBlock< long[] > keyBlock = new LongArrayDataBlock( new int[] { keys.length, 1 }, new long[] { 0, 0 }, keys );
				final DataBlock< long[] > valueBlock = new LongArrayDataBlock( new int[] { values.length, 1 }, new long[] { 0, 1 }, values );
				writer.writeBlock( dataset, attrs, keyBlock );
				writer.writeBlock( dataset, attrs, valueBlock );
			}
			catch ( final IOException e )
			{
				throw new RuntimeException( e );
			}
		};

		final long[] keys;
		final long[] values;
		LOG.debug( "Found fragment segment assingment dataset {}? {}", dataset, writer.datasetExists( dataset ) );
		if ( writer.datasetExists( dataset ) )
		{
			final DatasetAttributes attrs = writer.getDatasetAttributes( dataset );
			final int numEntries = ( int ) attrs.getDimensions()[ 0 ];
			keys = new long[ numEntries ];
			values = new long[ numEntries ];
			LOG.debug( "Found {} assignments", numEntries );
			final RandomAccessibleInterval< UnsignedLongType > data = N5Utils.open( writer, dataset );

			final Cursor< UnsignedLongType > keysCursor = Views.flatIterable( Views.hyperSlice( data, 1, 0l ) ).cursor();
			for ( int i = 0; keysCursor.hasNext(); ++i )
			{
				keys[ i ] = keysCursor.next().get();
			}

			final Cursor< UnsignedLongType > valuesCursor = Views.flatIterable( Views.hyperSlice( data, 1, 1l ) ).cursor();
			for ( int i = 0; valuesCursor.hasNext(); ++i )
			{
				values[ i ] = valuesCursor.next().get();
			}
		}
		else
		{
			keys = new long[] {};
			values = new long[] {};
		}

		return new FragmentSegmentAssignmentOnlyLocal( keys, values, persister );
	}

	public static IdService idService( final N5Writer n5, final String dataset ) throws IOException
	{
		final Long maxId = n5.getAttribute( dataset, MAX_ID_KEY, Long.class );
		final long actualMaxId;
		if ( maxId == null )
		{
			final boolean isMultiscale = isMultiScale( n5, dataset );
			final String dsPath = dataset;
			// isMultiscale ? Paths.get( dataset, getCoarsestLevel( n5, dataset
			// ) ).toString() : dataset;
			final DatasetAttributes attributes = n5.getDatasetAttributes( dsPath );
			LOG.debug( "is multiscale? {} dsPath={}", isMultiscale, dsPath );
			if ( isLabelMultisetType( n5, dsPath, isMultiscale ) )
			{
				LOG.debug( "Getting id service for label multisets" );
				actualMaxId = maxIdLabelMultiset( n5, dsPath );
				LOG.debug( "Got max id={}", actualMaxId );
			}
			else if ( isIntegerType( attributes.getDataType() ) )
			{
				actualMaxId = maxId( n5, dsPath );
			}
			else
			{
				return null;
			}
		}
		else
		{
			actualMaxId = maxId;
		}
		return new N5IdService( n5, dataset, actualMaxId );

	}

	private static < T extends IntegerType< T > & NativeType< T > > long maxId( final N5Reader n5, final String dataset ) throws IOException
	{
		final String ds;
		if ( n5.datasetExists( dataset ) )
		{
			ds = dataset;
		}
		else
		{
			final String[] scaleDirs = N5Helpers.listAndSortScaleDatasets( n5, dataset );
			ds = Paths.get( dataset, scaleDirs ).toString();
		}
		final RandomAccessibleInterval< T > data = N5Utils.open( n5, ds );
		long maxId = 0;
		for ( final T label : Views.flatIterable( data ) )
		{
			maxId = IdService.max( label.getIntegerLong(), maxId );
		}
		return maxId;
	}

	private static long maxIdLabelMultiset( final N5Reader n5, final String dataset ) throws IOException
	{
		final String ds;
		if ( n5.datasetExists( dataset ) )
		{
			ds = dataset;
		}
		else
		{
			final String[] scaleDirs = N5Helpers.listAndSortScaleDatasets( n5, dataset );
			ds = Paths.get( dataset, scaleDirs[ scaleDirs.length - 1 ] ).toString();
		}

		LOG.debug( "Looking for max id at n5={} dataset={}", n5, dataset );

		final DatasetAttributes attrs = n5.getDatasetAttributes( ds );
		final N5CacheLoader loader = new N5CacheLoader( n5, ds );
		final BoundedSoftRefLoaderCache< Long, Cell< VolatileLabelMultisetArray > > cache = new BoundedSoftRefLoaderCache<>( 1 );
		final LoaderCacheAsCacheAdapter< Long, Cell< VolatileLabelMultisetArray > > wrappedCache = new LoaderCacheAsCacheAdapter<>( cache, loader );
		final CachedCellImg< LabelMultisetType, VolatileLabelMultisetArray > data = new CachedCellImg<>(
				new CellGrid( attrs.getDimensions(), attrs.getBlockSize() ),
				new LabelMultisetType(),
				wrappedCache,
				new VolatileLabelMultisetArray( 0, true ) );
		long maxId = 0;
		for ( final Cell< VolatileLabelMultisetArray > cell : Views.iterable( data.getCells() ) )
		{
			for ( final long id : cell.getData().containedLabels() )
			{
				if ( id > maxId )
				{
					maxId = id;
				}
			}
		}
		return maxId;
	}

	public static String getFinestLevel(
			final N5Reader n5,
			final String dataset ) throws IOException
	{
		final String[] scaleDirs = listAndSortScaleDatasets( n5, dataset );
		return Paths.get( dataset, scaleDirs[ 0 ] ).toString();
	}

	public static String getCoarsestLevel(
			final N5Reader n5,
			final String dataset ) throws IOException
	{
		final String[] scaleDirs = listAndSortScaleDatasets( n5, dataset );
		return Paths.get( dataset, scaleDirs[ scaleDirs.length - 1 ] ).toString();
	}

	public static double[] getDoubleArrayAttribute( final N5Reader n5, final String dataset, final String key, final double... fallBack ) throws IOException
	{
		return Optional.ofNullable( n5.getAttribute( dataset, key, double[].class ) ).orElse( fallBack );
	}

	public static double[] getResolution( final N5Reader n5, final String dataset ) throws IOException
	{
		return getDoubleArrayAttribute( n5, dataset, RESOLUTION_KEY, 1.0, 1.0, 1.0 );
	}

	public static double[] getOffset( final N5Reader n5, final String dataset ) throws IOException
	{
		return getDoubleArrayAttribute( n5, dataset, OFFSET_KEY, 0.0, 0.0, 0.0 );
	}

	public static double[] getDownsamplingFactors( final N5Reader n5, final String dataset ) throws IOException
	{
		return getDoubleArrayAttribute( n5, dataset, DOWNSAMPLING_FACTORS_KEY, 1.0, 1.0, 1.0 );
	}

	public static AffineTransform3D fromResolutionAndOffset( final double[] resolution, final double[] offset )
	{
		return new AffineTransform3D().concatenate( new ScaleAndTranslation( resolution, offset ) );
	}

}
