package bdv.bigcat.viewer;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;

import com.sun.javafx.application.PlatformImpl;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.bigcat.viewer.bdvfx.KeyTracker;
import bdv.bigcat.viewer.ortho.OrthoView;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;

public class ExampleApplicationOrtho
{
	/** logger */
	static Logger LOGGER;

	public static void main( final String[] args ) throws Exception
	{
		// Set the log level
		final String rawFile = "data/sample_B_20160708_frags_46_50.hdf";
		PlatformImpl.startup( () -> {} );
		final String rawDataset = "volumes/raw";

		final double[] resolution = { 4, 4, 40 };
		final int[] cellSize = { 145, 53, 5 };
		final int numPriorities = 20;
		final SharedQueue sharedQueue = new SharedQueue( 12, numPriorities );
		final VolatileGlobalCellCache cellCache = new VolatileGlobalCellCache( 1, 12 );

		final RandomAccessibleIntervalDataSource< UnsignedByteType, VolatileUnsignedByteType > rawSource =
				DataSource.createH5RawSource( "raw", rawFile, rawDataset, cellSize, resolution, sharedQueue, numPriorities - 1 );

		final double[] min = Arrays.stream( Intervals.minAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final AffineTransform3D affine = new AffineTransform3D();
		rawSource.getSourceTransform( 0, 0, affine );
		affine.apply( min, min );
		affine.apply( max, max );
		final KeyTracker keyTracker = new KeyTracker();
		final OrthoView ortho = new OrthoView( ViewerOptions.options(), sharedQueue, keyTracker );
		ortho.sceneProperty().addListener( ( obs, oldv, newv ) -> {
			if ( oldv != null )
				keyTracker.removeFrom( oldv );
			if ( newv != null )
				keyTracker.installInto( newv );
		} );

		final CountDownLatch latch = new CountDownLatch( 1 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			final Scene scene = new Scene( ortho );
			stage.setScene( scene );
			stage.show();
			latch.countDown();
		} );
		latch.await();

		ortho.getState().addSource( new SourceAndConverter<>( rawSource, new RealARGBConverter<>( 0, 255 ) ) );
	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

}
