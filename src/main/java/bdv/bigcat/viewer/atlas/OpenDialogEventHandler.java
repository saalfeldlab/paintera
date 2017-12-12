package bdv.bigcat.viewer.atlas;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
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

	public OpenDialogEventHandler( final Atlas viewer, final SharedQueue cellCache, final Predicate< Event > check )
	{
		this( viewer, cellCache, check, true );
	}

	public OpenDialogEventHandler( final Atlas viewer, final SharedQueue cellCache, final Predicate< Event > check, final boolean consume )
	{
		super();
		this.viewer = viewer;
		this.cellCache = cellCache;
		this.check = check;
		this.consume = consume;
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

					try
					{
						final double min = meta.min();
						final double max = meta.max();
						final Collection< ? extends DataSource< ? extends RealType< ? >, ? extends RealType< ? > > > raws = dataset.get().getRaw(
								openDialog.getName(),
								meta.getResolution(),
								meta.getOffset(),
								meta.getAxisOrder(),
								cellCache,
								cellCache.getNumPriorities() - 1 );
						viewer.addRawSources( ( Collection ) raws, min, max );
					}
					catch ( final IOException e )
					{
						e.printStackTrace();
					}
					break;
				case LABEL:
					try
					{
						final Collection< LabelDataSource< ?, ? > > optionalSource = dataset.get().getLabels(
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
					catch ( final IOException e )
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				default:
					break;
				}
			}
		}
	}

}
