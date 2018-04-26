package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.util.Colors;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class CrosshairConfigNode
{

	private final TitledPane contents;

	public CrosshairConfigNode( final CrosshairConfig config )
	{
		super();

		final CheckBox showCrosshairs = new CheckBox();
		showCrosshairs.selectedProperty().bindBidirectional( config.showCrosshairsProperty() );

		final GridPane grid = new GridPane();

		final ColorPicker onFocusColorPicker = new ColorPicker();
		onFocusColorPicker.valueProperty().bindBidirectional( config.onFocusColorProperty() );
		onFocusColorPicker.setMaxWidth( 40 );
		onFocusColorPicker.getCustomColors().addAll( Colors.cremi( 1.0 ), Colors.cremi( 0.5 ) );

		final ColorPicker outOfFocusColorPicker = new ColorPicker();
		outOfFocusColorPicker.valueProperty().bindBidirectional( config.outOfFocusColorProperty() );
		outOfFocusColorPicker.setMaxWidth( 40 );
		outOfFocusColorPicker.getCustomColors().addAll( Colors.cremi( 1.0 ), Colors.cremi( 0.5 ) );

		final Label onFocusLabel = new Label( "on focus" );
		final Label offFocusLabel = new Label( "off focus" );
		grid.add( onFocusLabel, 0, 1 );
		grid.add( offFocusLabel, 0, 2 );

		grid.add( onFocusColorPicker, 1, 1 );
		grid.add( outOfFocusColorPicker, 1, 2 );

		GridPane.setHgrow( onFocusLabel, Priority.ALWAYS );
		GridPane.setHgrow( offFocusLabel, Priority.ALWAYS );

		contents = new TitledPane( "crosshair", grid );
		contents.setGraphic( showCrosshairs );
		contents.setExpanded( false );

	}

	public Node getContents()
	{
		return contents;
	}

}
