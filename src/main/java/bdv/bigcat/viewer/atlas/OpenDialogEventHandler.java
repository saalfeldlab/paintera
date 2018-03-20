package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.opendialog.BackendDialog;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog;
import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel;
import bdv.bigcat.viewer.meshes.MeshGenerator.ShapeKey;
import bdv.bigcat.viewer.meshes.cache.CacheUtils;
import bdv.bigcat.viewer.util.HashWrapper;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import bdv.util.volatiles.SharedQueue;
import javafx.event.Event;
import javafx.event.EventHandler;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.util.Pair;

public class OpenDialogEventHandler implements EventHandler< Event >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final Atlas viewer;

	private final SharedQueue cellCache;

	private final Predicate< Event > check;

	private final boolean consume;

	private final Consumer< Exception > exceptionHandler;

	public OpenDialogEventHandler( final Atlas viewer, final SharedQueue cellCache, final Predicate< Event > check )
	{
		this( viewer, cellCache, check, e -> {}, true );
	}

	public OpenDialogEventHandler( final Atlas viewer, final SharedQueue cellCache, final Predicate< Event > check, final Consumer< Exception > exceptionHandler, final boolean consume )
	{
		super();
		this.viewer = viewer;
		this.cellCache = cellCache;
		this.check = check;
		this.consume = consume;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void handle( final Event event )
	{
		if ( check.test( event ) )
		{
			if ( consume )
				event.consume();
			final OpenSourceDialog openDialog = new OpenSourceDialog();
			final Optional< BackendDialog > dataset = openDialog.showAndWait();
			if ( dataset.isPresent() )
			{
				final MetaPanel meta = openDialog.getMeta();
				switch ( openDialog.getType() )
				{
				case RAW:

					final double min = meta.min();
					final double max = meta.max();
					try
					{
						final Collection< ? extends DataSource< ? extends RealType< ? >, ? extends RealType< ? > > > raws = dataset.get().getRaw(
								openDialog.getName(),
								cellCache,
								cellCache.getNumPriorities() - 1 );
						viewer.addRawSources( ( Collection ) raws, min, max );
					}
					catch ( final Exception e )
					{
						exceptionHandler.accept( e );
					}
					break;
				case LABEL:
					try
					{
						if ( openDialog.isLabelMultiset() )
						{
							LOG.warn( "Loading multiset dataset!" );
							addLabelMultisetSource( viewer, dataset.get(), openDialog, meta, cellCache );
						}
						else
							addLabelSource( viewer, dataset.get(), openDialog, meta, cellCache );
					}
					catch ( final Exception e )
					{
						exceptionHandler.accept( e );
					}
					break;
				default:
					break;
				}
			}
		}
	}

	private static < I extends IntegerType< I > & NativeType< I >, V extends AbstractVolatileRealType< I, V > > void addLabelSource(
			final Atlas viewer,
			final BackendDialog dataset,
			final OpenSourceDialog openDialog,
			final MetaPanel meta,
			final SharedQueue cellCache ) throws Exception
	{
		// TODO handle this better!
		try
		{
			final Collection< ? extends LabelDataSource< I, V > > optionalSource = ( Collection< ? extends LabelDataSource< I, V > > ) dataset.getLabels(
					openDialog.getName(),
					cellCache,
					cellCache.getNumPriorities() );
			for ( final LabelDataSource< I, V > source : optionalSource )
				addLabelSource( viewer, source, openDialog.paint() ? openDialog.canvasCacheDirectory() : null, dataset.idService(), dataset.commitCanvas() );
		}
		catch ( final Exception e )
		{
			LOG.warn( "Could not add label source: " + e.getMessage() );
			e.printStackTrace();
		}
	}

	private static < I extends IntegerType< I > & NativeType< I >, V extends AbstractVolatileRealType< I, V > > void addLabelSource(
			final Atlas viewer,
			final LabelDataSource< I, V > lsource,
			final String cacheDir,
			final IdService idService,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{
		if ( cacheDir != null )
		{
			final int[] blockSize = { 64, 64, 64 };
			LOG.debug( "Adding canvas source with cache dir={}", cacheDir );
			viewer.addLabelSource(
					Atlas.addCanvas( lsource, blockSize, cacheDir, mergeCanvasIntoBackground ),
					lsource.getAssignment(),
					dt -> dt.get().getIntegerLong(),
					idService,
					null,
					null );
		}
		else
			viewer.addLabelSource( lsource, lsource.getAssignment(), dt -> dt.get().getIntegerLong(), idService, null, null );
	}

	private static void addLabelMultisetSource(
			final Atlas viewer,
			final BackendDialog dataset,
			final OpenSourceDialog openDialog,
			final MetaPanel meta,
			final SharedQueue cellCache ) throws Exception
	{
		LOG.warn( "Adding label multiset source" );
		// TODO handle this better!
		try
		{
			final Collection< ? extends LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > > optionalSource =
					( Collection< ? extends LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > > ) dataset.getLabels(
							openDialog.getName(),
							cellCache,
							cellCache.getNumPriorities() );
			for ( final LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > source : optionalSource )
				addLabelMultisetSource(
						viewer,
						source,
						openDialog.paint() ? openDialog.canvasCacheDirectory() : null,
						openDialog.paint() ? dataset.idService() : new LocalIdService(), dataset.commitCanvas() );
		}
		catch ( final Exception e )
		{
			LOG.warn( "Could not add label multiset source: " + e.getMessage() );
			e.printStackTrace();
		}
	}

	private static void addLabelMultisetSource(
			final Atlas viewer,
			final LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > lsource,
			final String cacheDir,
			final IdService idService,
			final BiConsumer< CachedCellImg< UnsignedLongType, ? >, long[] > mergeCanvasIntoBackground )
	{
		LOG.warn( "Adding label multiset source, maybe masked: {} {}", cacheDir, idService );
		final Function< Long, Interval[] >[] blockListCaches = getBlockListCaches( lsource, viewer.generalPurposeExecutorService() );
		final int[][] cubeSizes = Stream.generate( () -> new int[] { 1, 1, 1 } ).limit( blockListCaches.length ).toArray( int[][]::new );
		final Function< ShapeKey, Pair< float[], float[] > >[] meshCaches = blockListCaches == null ? null : CacheUtils.meshCacheLoaders( lsource, cubeSizes, lbl -> ( src, tgt ) -> tgt.set( src.contains( lbl ) ), CacheUtils::toCacheSoftRefLoaderCache );
		if ( cacheDir != null )
		{
			final int[] blockSize = { 64, 64, 64 };
			LOG.debug( "Adding canvas source with cache dir={}", cacheDir );
			viewer.addLabelSource(
					Atlas.addCanvasLabelMultiset( lsource, blockSize, cacheDir, mergeCanvasIntoBackground ),
					lsource.getAssignment(),
					idService,
					null,
					null );
		}
		else
			viewer.addLabelSource( lsource, lsource.getAssignment(), idService, blockListCaches, meshCaches );
	}

	public static < C extends Cell< VolatileLabelMultisetArray >, I extends RandomAccessible< C > & IterableInterval< C > > Function< Long, Interval[] >[] getBlockListCaches(
			final DataSource< LabelMultisetType, ? > source,
			final ExecutorService es )
	{
		final int numLevels = source.getNumMipmapLevels();
		if ( IntStream.range( 0, numLevels ).mapToObj( lvl -> source.getDataSource( 0, lvl ) ).filter( src -> !( src instanceof AbstractCellImg< ?, ?, ?, ? > ) ).count() > 0 )
			return null;

		final int[][] blockSizes = IntStream
				.range( 0, numLevels )
				.mapToObj( lvl -> ( AbstractCellImg< ?, ?, ?, ? > ) source.getDataSource( 0, lvl ) )
				.map( AbstractCellImg::getCellGrid )
				.map( OpenDialogEventHandler::blockSize )
				.toArray( int[][]::new );

		final double[][] scalingFactors = Atlas.scaleFactorsFromAffineTransforms( source );

		@SuppressWarnings( "unchecked" )
		final Function< HashWrapper< long[] >, long[] >[] uniqueIdCaches = new Function[ numLevels ];

		for ( int level = 0; level < numLevels; ++level )
		{
			@SuppressWarnings( "unchecked" )
			final AbstractCellImg< LabelMultisetType, VolatileLabelMultisetArray, C, I > img =
			( AbstractCellImg< LabelMultisetType, VolatileLabelMultisetArray, C, I > ) source.getDataSource( 0, level );
			uniqueIdCaches[ level ] = uniqueLabelLoaders( img );
		}

		return CacheUtils.blocksForLabelCaches( source, uniqueIdCaches, blockSizes, scalingFactors, CacheUtils::toCacheSoftRefLoaderCache, es );

	}

	public static int[] blockSize( final CellGrid grid )
	{
		final int[] blockSize = new int[ grid.numDimensions() ];
		Arrays.setAll( blockSize, grid::cellDimension );
		return blockSize;
	}

	public static < C extends Cell< VolatileLabelMultisetArray >, I extends RandomAccessible< C > & IterableInterval< C > >
	Function< HashWrapper< long[] >, long[] > uniqueLabelLoaders(
			final AbstractCellImg< LabelMultisetType, VolatileLabelMultisetArray, C, I > img )
	{
		final I cells = img.getCells();
		return location -> {
			final RandomAccess< C > access = cells.randomAccess();
			access.setPosition( location.getData() );
			final long[] labels = access.get().getData().containedLabels();
			LOG.debug( "Position={}: labels={}", location.getData(), labels );
			return labels;
		};
	}

}
