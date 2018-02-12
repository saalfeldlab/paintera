package bdv.bigcat.viewer.atlas;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.opendialog.BackendDialog;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog;
import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel;
import bdv.util.IdService;
import bdv.util.volatiles.SharedQueue;
import javafx.event.Event;
import javafx.event.EventHandler;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;

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
						// TODO also handle multisets!
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
			final Consumer< RandomAccessibleInterval< UnsignedLongType > > mergeCanvasIntoBackground )
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

}
