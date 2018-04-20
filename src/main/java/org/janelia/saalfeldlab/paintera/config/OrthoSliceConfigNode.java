package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class OrthoSliceConfigNode
{

	private final TitledPane contents;

	public OrthoSliceConfigNode( final OrthoSliceConfig config )
	{
		super();

		final CheckBox showOrthoViews = new CheckBox();
		showOrthoViews.selectedProperty().bindBidirectional( config.enableProperty() );

		final GridPane grid = new GridPane();

		final Label topLeftLabel = new Label( "top left" );
		final Label topRightLabel = new Label( "top right" );
		final Label bottomLeftLabel = new Label( "bottom left" );

		final CheckBox topLeftCheckBox = new CheckBox();
		final CheckBox topRightCheckBox = new CheckBox();
		final CheckBox bottomLeftCheckBox = new CheckBox();

		topLeftCheckBox.selectedProperty().bindBidirectional( config.showTopLeftProperty() );
		topRightCheckBox.selectedProperty().bindBidirectional( config.showTopRightProperty() );
		bottomLeftCheckBox.selectedProperty().bindBidirectional( config.showBottomLeftProperty() );

		grid.add( topLeftLabel, 0, 0 );
		grid.add( topRightLabel, 0, 1 );
		grid.add( bottomLeftLabel, 0, 2 );

		grid.add( topLeftCheckBox, 1, 0 );
		grid.add( topRightCheckBox, 1, 1 );
		grid.add( bottomLeftCheckBox, 1, 2 );

		GridPane.setHgrow( topLeftLabel, Priority.ALWAYS );
		GridPane.setHgrow( topRightLabel, Priority.ALWAYS );
		GridPane.setHgrow( bottomLeftLabel, Priority.ALWAYS );

		contents = new TitledPane( "orthoviews", grid );
		contents.setGraphic( showOrthoViews );
		contents.setExpanded( false );

	}

	public Node getContents()
	{
		return contents;
	}

}
