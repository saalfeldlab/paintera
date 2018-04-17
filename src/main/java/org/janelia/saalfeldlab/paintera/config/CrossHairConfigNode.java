package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CrossHairConfigNode
{

	private final VBox contents;

	public CrossHairConfigNode( final CrosshairConfig config )
	{
		super();

		final CheckBox showCrosshairs = new CheckBox();
		showCrosshairs.selectedProperty().bindBidirectional( config.showCrosshairsProperty() );

		final GridPane grid = new GridPane();

		grid.add( new Label( "show crosshairs" ), 0, 0 );
		grid.add( showCrosshairs, 1, 0 );

		final ColorPicker onFocusColorPicker = new ColorPicker();
		onFocusColorPicker.valueProperty().bindBidirectional( config.onFocusColorProperty() );
		onFocusColorPicker.setMaxWidth( 40 );

		final ColorPicker outOfFocusColorPicker = new ColorPicker();
		outOfFocusColorPicker.valueProperty().bindBidirectional( config.outOfFocusColorProperty() );
		outOfFocusColorPicker.setMaxWidth( 40 );

		final Label onFocusLabel = new Label( "on focus" );
		final Label offFocusLabel = new Label( "off focus" );
		grid.add( onFocusLabel, 0, 1 );
		grid.add( offFocusLabel, 0, 2 );

		grid.add( onFocusColorPicker, 1, 1 );
		grid.add( outOfFocusColorPicker, 1, 2 );

		GridPane.setHgrow( onFocusLabel, Priority.ALWAYS );
		GridPane.setHgrow( offFocusLabel, Priority.ALWAYS );

		contents = new VBox( grid );
	}

	public Node getContents()
	{
		return contents;
	}

}
