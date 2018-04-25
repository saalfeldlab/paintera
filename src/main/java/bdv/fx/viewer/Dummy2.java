package bdv.fx.viewer;

import java.nio.ByteBuffer;
import java.util.Random;

import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.util.MakeUnchecked;

import com.sun.javafx.image.PixelUtils;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.type.numeric.ARGBType;

public class Dummy2 extends Application
{

	public static void main( final String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{

		final BufferExposingWritableImage img = new BufferExposingWritableImage( 10, 20 );

		for ( int x = 0; x < img.getWidth(); ++x )
		{
			for ( int y = 0; y < img.getHeight(); ++y )
			{
				img.getPixelWriter().setArgb( x, y, 0xef << 24 | 1 << 16 | 2 << 8 | 3 );
			}
		}

		System.out.println( img.getPixelWriter().getPixelFormat().isPremultiplied() );
		img.getPixelWriter().setArgb( 0, 1, 123 );

		final int r = 100;
		final int g = 150;
		final int b = 200;
		final int a = 150;

		final ArrayImg< ARGBType, IntAccess > arrayImg = img.asArrayImg();
		final ArrayRandomAccess< ARGBType > access = arrayImg.randomAccess();
		access.setPosition( new long[] { 0, 0 } );
		access.get().set( ARGBType.rgba( r, g, b, a ) );
		access.setPosition( new long[] { 1, 0 } );
		access.get().set( PixelUtils.NonPretoPre( ARGBType.rgba( r, g, b, a ) ) );
		img.getPixelWriter().setColor( 2, 0, Color.rgb( r, g, b, a / 255.0 ) );

		System.out.println( img.getPixelReader().getColor( 0, 0 ) );
		System.out.println( img.getPixelReader().getColor( 1, 0 ) );
		System.out.println( img.getPixelReader().getColor( 2, 0 ) );

		System.exit( 123 );

		final ImageView view = new ImageView( img );
		final Group root = new Group();
		view.setFitWidth( 100 );
		view.setFitHeight( 200 );
		root.getChildren().setAll( new HBox( view ) );

		final Scene scene = new Scene( root );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();
		primaryStage.show();

		final Random rng = new Random();
		final ByteBuffer buffer = ( ByteBuffer ) img.getBuffer();
		new Thread( () -> {

			while ( true )
			{
//				final int x = rng.nextInt( ( int ) img.getWidth() );
//				final int y = rng.nextInt( ( int ) img.getHeight() );
//				img.getPixelWriter().setArgb( x, y, rng.nextInt() );
				final int index = rng.nextInt( buffer.asIntBuffer().capacity() );
				final int val = rng.nextInt();
				System.out.println( "Setting index " + index + " to " + val );
				buffer.asIntBuffer().put( index, val );
				try
				{
					InvokeOnJavaFXApplicationThread.invokeAndWait( MakeUnchecked.unchecked( () -> {
						img.setPixelsDirty();
					} ) );
					Thread.sleep( 100 );
				}
				catch ( final InterruptedException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} ).start();

	}

}
