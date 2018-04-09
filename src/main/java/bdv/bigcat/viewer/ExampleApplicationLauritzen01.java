package bdv.bigcat.viewer;

import static net.imglib2.cache.img.AccessFlags.VOLATILE;
import static net.imglib2.cache.img.PrimitiveType.LONG;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.sun.javafx.application.PlatformImpl;

import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.label.Label;
import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSourceFromDelegates;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.atlas.opendialog.VolatileHelpers;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentOnlyLocal;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.net.imglib2.util.ValueTriple;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.util.volatiles.SharedQueue;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.ArrayDataAccessFactory;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.converter.Converters;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileLongArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class ExampleApplicationLauritzen01
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static void main( final String[] args ) throws Exception
	{
		PlatformImpl.startup( () -> {} );

		final String USER_HOME = System.getProperty( "user.home" );

		String n5Path = "/nrs/saalfeld/lauritzen/03/workspace.n5";
		String rawGroup = "/filtered/volumes/raw";
//		String n5Path = "/nrs/saalfeld/sample_E/sample_E.n5";
//		String rawGroup = "/volumes/raw";
		String labelsN5Path = "/nrs/saalfeld/lauritzen/03/workspace.n5";
		String labelsDataset = "/filtered/volumes/labels/multiset-mipmap";
//		String labelsN5Path = "/groups/saalfeld/saalfeldlab/sampleE/multicut_segmentation.n5";
//		String labelsDataset = "/multicut";
		final String cleftsN5Path = "/nrs/saalfeld/lauritzen/03/workspace.n5";
		final String cleftsDataset = "/syncleft_dist_160000";
//		final String cleftsDataset = "/syncleft_dist_DTU-2_200000";
//		final String cleftsN5Path = "/nrs/saalfeld/sample_E/sample_E.n5";
//		final String cleftsDataset = "/syncleft_dist_160000";

		final double[] resolution = { 4, 4, 40 };

		final Parameters params = getParameters( args );
		if ( params != null )
		{
			LOG.info( "parameters are not null" );
			n5Path = params.filePath;
			rawGroup = params.rawDatasetPath;
			labelsN5Path = n5Path;
			labelsDataset = params.labelDatasetPath;
			resolution[ 0 ] = params.resolution.get( 0 );
			resolution[ 1 ] = params.resolution.get( 1 );
			resolution[ 2 ] = params.resolution.get( 2 );

			System.out.println( Arrays.toString( resolution ) );
		}

		final int numPriorities = 20;
		final SharedQueue sharedQueue = new SharedQueue( 8, 20 );

		final RandomAccessibleIntervalDataSource< UnsignedByteType, VolatileUnsignedByteType > rawSource =
				DataSource.createN5MipmapSource( "raw", new N5FSReader( n5Path ), rawGroup, resolution, sharedQueue, UnsignedByteType::new, VolatileUnsignedByteType::new );

		final RandomAccessibleIntervalDataSource< FloatType, VolatileFloatType > cleftSource =
				DataSource.createN5Source( "clefts", new N5FSReader( cleftsN5Path ), cleftsDataset, resolution, sharedQueue, 0 );

		final ValueTriple< RandomAccessibleInterval< LabelMultisetType >[], RandomAccessibleInterval< VolatileLabelMultisetType >[], AffineTransform3D[] > labels =
				VolatileHelpers.loadMultiscaleMultisets( new N5FSReader( labelsN5Path ), labelsDataset, resolution, new double[ resolution.length ], sharedQueue, 0 );

		final RandomAccessibleIntervalDataSource< LabelMultisetType, VolatileLabelMultisetType > labelsDataSource = new RandomAccessibleIntervalDataSource<>(
				labels.getA(),
				labels.getB(),
				labels.getC(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				"labels" );
		final LabelDataSourceFromDelegates< LabelMultisetType, VolatileLabelMultisetType > labelSource =
				new LabelDataSourceFromDelegates<>( labelsDataSource, new FragmentSegmentAssignmentOnlyLocal( ( k, v ) -> {} ) );

		final IdService idService = new LocalIdService();

		final Atlas viewer = new Atlas( sharedQueue );

		final CountDownLatch latch = new CountDownLatch( 5 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}

			viewer.addRawSource( rawSource, 0., ( 1 << 8 ) - 1. );
			viewer.addLabelSource( labelSource, labelSource.getAssignment(), idService, null, null );
			viewer.addRawSource( cleftSource, 0., ( 1 << 8 ) - 1. );
			final ARGBColorConverter< ? > converter = ( ARGBColorConverter< ? > ) viewer.sourceInfo().getState( cleftSource ).converterProperty().get();
			converter.colorProperty().set( new ARGBType( 0xffff0080 ) );
			converter.minProperty().set( 0.0 );
			converter.maxProperty().set( 0.2 );

			final RandomAccessibleInterval< UnsignedLongType > thresholdedClefts = Converters.convert(
					cleftSource.getDataSource( 0, 0 ),
					( a, b ) -> b.set( a.getRealDouble() > converter.minProperty().get() ? 1L : Label.TRANSPARENT ),
					new UnsignedLongType() );

			try
			{
				final SelectedIds selectedIds = viewer.getSelectedIds( labelSource ).get();
				final CachedCellImg< UnsignedLongType, VolatileLongArray > cleftSelection = volatileCleftSelection(
						Converters.convert(
								labels.getA()[ 0 ],
								( a, b ) -> b.set( a.entrySet().iterator().next().getElement().id() ),
								new UnsignedLongType() ),
						thresholdedClefts,
						new N5FSReader( cleftsN5Path ).getDatasetAttributes( cleftsDataset ).getBlockSize(),
						// TODO add selectedIds here
						selectedIds );
				selectedIds.addListener( () -> {
					cleftSelection.getCache().invalidateAll();
				} );
				converter.minProperty().addListener( ( self, oldValue, newValue ) -> {
					cleftSelection.getCache().invalidateAll();
				} );

				final RandomAccessibleIntervalDataSource< UnsignedLongType, VolatileUnsignedLongType > cleftSelectionSource =
						DataSource.createDataSource(
								"thresholded clefts",
								cleftSelection,
								resolution,
								sharedQueue, 0 );

//				final AtlasSourceState< ?, ? > labelState = viewer.sourceInfo().getState( labelSource );

//				final Cache< Long, Interval[] > labelLocations = labelState.blocklistCacheProperty().get()[ 0 ];
				// labelState.blocklistCacheProperty().get().length - 1 ];

//				final SelectedIds labelSelection = labelState.selectedIdsProperty().get();
//				final ObjectProperty< long[] > currentLabelSelection = new SimpleObjectProperty<>();
//				labelSelection.addListener( () -> currentLabelSelection.set( labelSelection.getActiveIds() ) );
//				currentLabelSelection.set( labelSelection.getActiveIds() );

//				@SuppressWarnings( "unchecked" )
//				final Cache< Long, Interval[] >[] cleftsLocations = new Cache[] { new Cache< Long, Interval[] >()
//				{
//
//					@Override
//					public Interval[] getIfPresent( final Long key )
//					{
//						try
//						{
//							return get( key );
//						}
//						catch ( final ExecutionException e )
//						{
//							throw new RuntimeException( e );
//						}
//					}
//
//					@Override
//					public void invalidateAll()
//					{
//						// TODO Auto-generated method stub
//
//					}
//
//					@Override
//					public Interval[] get( final Long key ) throws ExecutionException
//					{
//						// TODO id is just hard coded for test purposes
//						final long[] sel = { 106947 };// currentLabelSelection.get();
//						if ( sel == null )
//							throw new ExecutionException( "Current selection is null: " + sel, null );
//						if ( sel.length == 0 )
//							throw new ExecutionException( "No ids selected currently!", null );
//
//						final long relevantId = sel[ 0 ];
//						final Interval[] locations = labelLocations.get( relevantId );
//						System.out.println( "Relevant id: " + relevantId + " locations: " + Arrays.toString( locations ) );
//						return locations;
//
//					}
//				}
//				};
//
//				// block size is hard coded to 64 cubed. Will have to adapt to
//				// variable block sizes later on.
//				final Cache< ShapeKey, Pair< float[], float[] > >[] meshCaches = CacheUtils.meshCacheLoaders(
//						cleftSelectionSource,
//						new int[][] { { 64, 64, 64 } },
//						cleftLabel -> ( s, t ) -> t.set( s.get() > 0 ),
//						CacheUtils::toCacheSoftRefLoaderCache );
//
				viewer.addGenericSource( cleftSelectionSource, ( s, t ) -> t.set( s.get().get() > 0 ? 0xffffffff : 0 ), new ARGBCompositeAlphaYCbCr() );
//
//				final AtlasSourceState< ?, ? > cleftState = viewer.sourceInfo().getState( cleftSelectionSource );
//
//				final MeshManagerSimple cleftMeshManager = new MeshManagerSimple(
//						cleftsLocations,
//						meshCaches,
//						viewer.get3DViewer().meshesGroup(),
//						new SimpleIntegerProperty( 1 ),
//						viewer.generalPurposeExecutorService() );
//				cleftMeshManager.generateMesh( 1 );
//				currentLabelSelection.addListener( ( obs, oldv, newv ) -> {
//					if ( Optional.ofNullable( newv ).orElse( new long[ 0 ] ).length == 0 )
//						cleftMeshManager.removeAllMeshes();
//					else
//					{
//						System.out.println( "Generating mesh: OK OK!" );
//						cleftMeshManager.generateMesh( 1 );
//					}
//				} );
//				cleftState.meshManagerProperty().set( cleftMeshManager );
			}
			catch ( final IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			stage.show();
			latch.countDown();
		} );

		latch.await();
	}

	private static Parameters getParameters( final String[] args )
	{
		// get the parameters
		final Parameters params = new Parameters();
		JCommander.newBuilder()
		.addObject( params )
		.build()
		.parse( args );

		final boolean success = validateParameters( params );
		if ( !success ) {
			return null;
		}

		return params;
	}

	private static boolean validateParameters( final Parameters params )
	{
		return params.filePath != "";
	}

	public static class LabelIntersectionCellLoader implements CellLoader< UnsignedLongType >
	{

		private final RandomAccessible< UnsignedLongType > label1;

		private final RandomAccessible< UnsignedLongType > label2;

		private final SelectedIds selectedIds;

		public LabelIntersectionCellLoader(
				final RandomAccessible< UnsignedLongType > label1,
				final RandomAccessible< UnsignedLongType > label2,
				final SelectedIds selectedIds )
		{

			this.label1 = label1;
			this.label2 = label2;
			this.selectedIds = selectedIds;
		}

		@Override
		public void load( final SingleCellArrayImg< UnsignedLongType, ? > cell ) throws Exception
		{

			final IntervalView< UnsignedLongType > label2Interval = Views.interval( label2, cell );
			final Cursor< UnsignedLongType > label1Cursor = Views.flatIterable( Views.interval( label1, cell ) ).cursor();
			final Cursor< UnsignedLongType > label2Cursor = Views.flatIterable( label2Interval ).cursor();
			final Cursor< UnsignedLongType > targetCursor = cell.localizingCursor();

			cell.forEach( px -> px.setInteger( Label.TRANSPARENT ) );
			while ( targetCursor.hasNext() )
			{
				final UnsignedLongType targetType = targetCursor.next();
				final UnsignedLongType label1Type = label1Cursor.next();
				final UnsignedLongType label2Type = label2Cursor.next();
				if ( targetType.get() == Label.TRANSPARENT )
				{
					if ( selectedIds.isActive( label1Type.get() ) && label2Type.get() == 1 )
					{
						// if (label2Type.get() == 1 ) {
						FloodFill.< UnsignedLongType, UnsignedLongType >fill(
								Views.extendValue( label2Interval, new UnsignedLongType( Label.TRANSPARENT ) ),
								Views.extendValue( cell, new UnsignedLongType() ),
								targetCursor,
								new UnsignedLongType( 1 ),
								new DiamondShape( 1 ),
								// first element in pair is current pixel,
								// second element is reference
								( p1, p2 ) -> p1.getB().get() == Label.TRANSPARENT && p1.getA().get() == 1 );
					}
				}
			}
		}
	}

	public static final CachedCellImg< UnsignedLongType, LongArray > cleftSelection(
			final RandomAccessibleInterval< UnsignedLongType > segments,
			final RandomAccessibleInterval< UnsignedLongType > clefts,
			final int[] blockSize,
			final SelectedIds selectedIds ) throws IOException
	{
		final long[] dimensions = Intervals.dimensionsAsLongArray( clefts );

		final LabelIntersectionCellLoader loader = new LabelIntersectionCellLoader( segments, clefts, selectedIds );

		final CellGrid grid = new CellGrid( dimensions, blockSize );

		final UnsignedLongType type = new UnsignedLongType();
		final Cache< Long, Cell< LongArray > > cache =
				new SoftRefLoaderCache< Long, Cell< LongArray > >()
				.withLoader( LoadedCellCacheLoader.get( grid, loader, type ) );

		final CachedCellImg< UnsignedLongType, LongArray > img = new CachedCellImg<>( grid, type, cache, ArrayDataAccessFactory.get( LONG ) );

		return img;
	}

	public static final CachedCellImg< UnsignedLongType, VolatileLongArray > volatileCleftSelection(
			final RandomAccessibleInterval< UnsignedLongType > segments,
			final RandomAccessibleInterval< UnsignedLongType > clefts,
			final int[] blockSize,
			final SelectedIds selectedIds )
	{
		final long[] dimensions = Intervals.dimensionsAsLongArray( clefts );

		final LabelIntersectionCellLoader loader = new LabelIntersectionCellLoader( segments, clefts, selectedIds );

		final CellGrid grid = new CellGrid( dimensions, blockSize );

		final UnsignedLongType type = new UnsignedLongType();
		final Cache< Long, Cell< VolatileLongArray > > cache =
				new SoftRefLoaderCache< Long, Cell< VolatileLongArray > >()
				.withLoader( LoadedCellCacheLoader.get( grid, loader, type, VOLATILE ) );

		final CachedCellImg< UnsignedLongType, VolatileLongArray > img = new CachedCellImg<>( grid, type, cache, ArrayDataAccessFactory.get( LONG, VOLATILE ) );

		return img;
	}
}
