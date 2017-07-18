package bdv.bigcat.viewer;

import java.util.HashSet;

import org.dockfx.DockPane;

import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Atlas
{

	private final BaseView view;

	private final HashSet< Source< ? > > sources;

	public Atlas()
	{
		super();
		this.view = new BaseView();
		this.sources = new HashSet<>();
	}

	public void start( final Stage primaryStage ) throws Exception
	{

		final Scene scene = view.createScene( 800, 600 );

		primaryStage.setTitle( "ATLAS" );
		primaryStage.setScene( scene );
		primaryStage.sizeToScene();

		view.makeDefaultLayout();

		primaryStage.show();

		// test the look and feel with both Caspian and Modena
		Application.setUserAgentStylesheet( Application.STYLESHEET_CASPIAN );
//		Application.setUserAgentStylesheet( Application.STYLESHEET_MODENA );
		// initialize the default styles for the dock pane and undocked nodes
		// using the DockFX
		// library's internal Default.css stylesheet
		// unlike other custom control libraries this allows the user to
		// override them globally
		// using the style manager just as they can with internal JavaFX
		// controls
		// this must be called after the primary stage is shown
		// https://bugs.openjdk.java.net/browse/JDK-8132900
		DockPane.initializeDefaultUserAgentStylesheet();

	}

	// this needs to be rewritten to addDataset( DatasetSpec spec );
	public void addSource( final SourceAndConverter< ? > source )
	{
		final ARGBCompositeAlphaYCbCr comp = new ARGBCompositeAlphaYCbCr();
		view.addSource( source, comp );
		this.sources.add( source.getSpimSource() );
	}

}
