package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.apache.commons.io.IOUtils;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import net.imglib2.Interval;
import net.imglib2.img.basictypeaccess.volatiles.array.DirtyVolatileByteArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;

public class BackendDialogDVID implements BackendDialog
{
	private final SimpleObjectProperty< String > dvid = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > dataset = new SimpleObjectProperty<>();

	private final SimpleObjectProperty< String > error = new SimpleObjectProperty<>();

	{
		dataset.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
				error.set( null );
			else
				error.set( "No raw dataset" );
		} );

		dvid.set( "" );
		dataset.set( "" );
		error.set( "" );
	}

	@Override
	public Node getDialogNode()
	{
		final TextField dvidPathField = new TextField( dvid.get() );
		dvidPathField.setMinWidth( 0 );
		dvidPathField.setMaxWidth( Double.POSITIVE_INFINITY );
		dvidPathField.textProperty().bindBidirectional( dvid );

		final TextField rawDatasetField = new TextField( dataset.get() );
		rawDatasetField.setMinWidth( 0 );
		rawDatasetField.setMaxWidth( Double.POSITIVE_INFINITY );
		rawDatasetField.textProperty().bindBidirectional( dataset );

		final GridPane grid = new GridPane();
		grid.add( new Label( "dvid path" ), 0, 0 );
		grid.add( new Label( "data set" ), 0, 1 );
		grid.setMinWidth( Region.USE_PREF_SIZE );
		grid.add( dvidPathField, 1, 0 );
		grid.add( rawDatasetField, 1, 1 );
		grid.setHgap( 10 );

		GridPane.setHgrow( dvidPathField, Priority.ALWAYS );
		GridPane.setHgrow( rawDatasetField, Priority.ALWAYS );

		return grid;
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return error;
	}

	@Override
	public < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String format = dvid.get();
		// TODO: define information that can be obtained from user
		final long[] minPoint = { 1728, 1536, 1344 };

		final Function< Interval, String > addressComposer = interval -> {
			final String address = String.format(
					format,
					interval.max( 0 ) - interval.min( 0 ) + 1,
					interval.max( 1 ) - interval.min( 1 ) + 1,
					interval.max( 2 ) - interval.min( 2 ) + 1,
					offset[ 0 ] + interval.min( 0 ),
					offset[ 1 ] + interval.min( 1 ),
					offset[ 2 ] + interval.min( 2 ) );
			System.out.println( "address: " + address );
			return address;
		};

		final BiConsumer< byte[], DirtyVolatileByteArray > copier = ( bytes, access ) -> {
			System.arraycopy( bytes, 0, access.getCurrentStorageArray(), 0, bytes.length );
			access.setDirty();
		};

		final HTTPLoader< DirtyVolatileByteArray > functor = new HTTPLoader<>( addressComposer, ( n ) -> new DirtyVolatileByteArray( ( int ) n, true ), copier );

		return Optional.empty();
	}

	@Override
	public Optional< LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return Optional.empty();
	}

	public class HTTPLoader< A > implements Function< Interval, A >
	{

		private final Function< Interval, String > addressComposer;

		private final LongFunction< A > accessFactory;

		private final BiConsumer< byte[], A > copyToAccess;

		public HTTPLoader(
				final Function< Interval, String > addressComposer,
				final LongFunction< A > accessFactory,
				final BiConsumer< byte[], A > copyToAccess )
		{
			super();
			this.addressComposer = addressComposer;
			this.accessFactory = accessFactory;
			this.copyToAccess = copyToAccess;
		}

		@Override
		public A apply( final Interval interval )
		{
			try
			{
				final String address = addressComposer.apply( interval );
				final URL url = new URL( address );
				final InputStream stream = url.openStream();
				final long numElements = Intervals.numElements( interval );
				final byte[] response = IOUtils.toByteArray( stream );

				final A access = accessFactory.apply( numElements );
				copyToAccess.accept( response, access );

				return access;
			}
			catch ( final Exception e )
			{
				throw new RuntimeException( e );
			}

		}

	}

}
