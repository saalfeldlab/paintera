package org.janelia.saalfeldlab.paintera.config;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class NavigationConfigNode
{

	private final TitledPane contents = new TitledPane( "Navigation", null );

	private final CheckBox allowRotationsCheckBox = new CheckBox();

	public NavigationConfigNode()
	{
		final GridPane grid = new GridPane();
		contents.setContent( grid );
		contents.setExpanded( false );

		int row = 0;
		{
			final Label label = new Label( "Rotations" );
			final Region spacer = new Region();
			grid.add( label, 0, row );
			grid.add( spacer, 1, row );
			grid.add( allowRotationsCheckBox, 2, row );
			GridPane.setHgrow( spacer, Priority.ALWAYS );
			++row;
		}
	}

	public void bind( final NavigationConfig config )
	{
		allowRotationsCheckBox.selectedProperty().bindBidirectional( config.allowRotationsProperty() );
	}

	public Node getContents()
	{
		return contents;
	}

}
