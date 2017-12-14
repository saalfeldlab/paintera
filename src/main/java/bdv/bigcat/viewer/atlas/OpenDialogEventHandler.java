package bdv.bigcat.viewer.atlas;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.opendialog.BackendDialog;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog;
import bdv.bigcat.viewer.atlas.opendialog.meta.MetaPanel;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.util.volatiles.SharedQueue;
import javafx.event.Event;
import javafx.event.EventHandler;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;

public class OpenDialogEventHandler implements EventHandler< Event >
{

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
								meta.getResolution(),
								meta.getOffset(),
								meta.getAxisOrder(),
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
						final Collection< ? extends LabelDataSource< ?, ? > > optionalSource = dataset.get().getLabels(
								openDialog.getName(),
								meta.getResolution(),
								meta.getOffset(),
								meta.getAxisOrder(),
								cellCache,
								cellCache.getNumPriorities() );
						for ( final LabelDataSource< ?, ? > source : optionalSource )
						{
							final Object t = source.getDataType();
							final Object vt = source.getType();
							if ( t instanceof LabelMultisetType && vt instanceof VolatileLabelMultisetType )
								viewer.addLabelSource( ( LabelDataSource< LabelMultisetType, VolatileLabelMultisetType > ) source );
							else if ( t instanceof IntegerType< ? > && vt instanceof AbstractVolatileRealType< ?, ? > )
								viewer.addLabelSource(
										( LabelDataSource ) source,
										( ToLongFunction ) ( ToLongFunction< ? extends AbstractVolatileRealType< ? extends IntegerType< ? >, ? > > ) dt -> dt.get().getIntegerLong() );
						}
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

}
