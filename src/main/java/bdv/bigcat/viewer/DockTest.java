package bdv.bigcat.viewer;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.dockfx.DockNode;
import org.dockfx.DockPane;
import org.dockfx.DockPos;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingNode;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DockTest extends Application
{

	public static class SomeSwingNode extends SwingNode
	{
		@Override
		public double minWidth( final double height )
		{
			return 10;
		}

		@Override
		public double minHeight( final double height )
		{
			return 10;
		}
	}

	public static void main( final String[] args )
	{
		launch( args );
	}

	@Override
	public void start( final Stage primaryStage ) throws Exception
	{
		final DockPane dock = new DockPane();


		final Label label = new Label( "BLABLA" );
		final DockNode dockedLabel = new DockNode( label );
		dockedLabel.dock( dock, DockPos.TOP );

		final Label label2 = new Label( "BLABLA2" );
		final DockNode dockedLabel2 = new DockNode( label2 );
		dockedLabel2.dock( dock, DockPos.BOTTOM );

		final SomeSwingNode sn = new SomeSwingNode();
		final JLabel label3 = new JLabel( "SWING STUFF!" );
		SwingUtilities.invokeLater( () -> {
			sn.setContent( label3 );
		} );
		label3.setVisible( true );
		label3.setEnabled( true );

		final Scene scene = new Scene( dock, 500, 500 );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		primaryStage.show();
//		Application.setUserAgentStylesheet( Application.STYLESHEET_MODENA );

		DockPane.initializeDefaultUserAgentStylesheet();


		final DockNode dockedLabel3 = new DockNode( sn );
		dockedLabel3.visibleProperty().addListener( ( ChangeListener< Boolean > ) ( observable, oldValue, newValue ) -> label3.setVisible( true ) );
		dockedLabel3.dock( dock, DockPos.BOTTOM );
	}

}
