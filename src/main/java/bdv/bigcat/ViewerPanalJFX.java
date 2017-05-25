package bdv.bigcat;

import java.util.Random;

import javax.swing.SwingUtilities;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.ViewerFrame;
import bdv.viewer.ViewerPanel;
import javafx.application.Application;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

@SuppressWarnings( "restriction" )
public class ViewerPanalJFX
{
	public static void main( final String[] args )
	{

		Application.launch( MyApplication.class );

	}

	public static class MyApplication extends Application
	{

		@Override
		public void start( final Stage primaryStage ) throws Exception
		{
			final StackPane root = new StackPane();

			final SwingNode swingNode = new SwingNode();
			createSwingContent( swingNode );
			root.getChildren().add( swingNode );
			final Scene scene = new Scene( root, 500, 500 );


			primaryStage.setTitle( "BDV" );
			primaryStage.setScene( scene );
			primaryStage.show();
		}

		private void createSwingContent( final SwingNode swingNode )
		{
			SwingUtilities.invokeLater( () -> {
				final Random rng = new Random( 100 );
				final Img< FloatType > rai = ArrayImgs.floats( 100, 200, 300 );
				final Img< FloatType > rai2 = ArrayImgs.floats( 100, 200, 300 );
				for ( final FloatType f : rai )
					f.set( rng.nextFloat() * ( 1 << 16 ) );
				for ( final FloatType f : rai2 )
					f.set( rng.nextFloat() * ( 1 << 15 ) );
				final BdvStackSource< FloatType > bdv = BdvFunctions.show( rai, "name" );
				bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
				BdvFunctions.show( rai2, "rai2", BdvOptions.options().addTo( bdv ) );
				final BdvHandleFrame handle = ( BdvHandleFrame ) bdv.getBdvHandle();
				final ViewerFrame frame = handle.getBigDataViewer().getViewerFrame();
				frame.setVisible( false );
				final ViewerPanel viewer = frame.getViewerPanel();
				frame.remove( viewer );
				swingNode.setContent( viewer );
				swingNode.setVisible( true );
				viewer.setVisible( true );
				viewer.getDisplay().setVisible( true );
				swingNode.resize( 500, 500 );
			} );
		}
	}
}
