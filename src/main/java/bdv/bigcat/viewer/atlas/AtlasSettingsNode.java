package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.atlas.mode.ModeUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

public class AtlasSettingsNode
{

	private final AtlasSettings settings;

	public AtlasSettingsNode( final AtlasSettings settings )
	{
		super();
		this.settings = settings;
	}

	public static Node getNode( final AtlasSettings settings )
	{
		return new AtlasSettingsNode( settings ).getNode();
	}

	public Node getNode()
	{
		final GridPane grid = new GridPane();

		int row = 0;
		addBooleanProperty( settings.allowRotationsProperty(), "Allow drag rotations.", grid, row++ );
		addSelection( settings.availableModes(), settings.currentModeProperty(), "Mode", grid, row++, Optional.of( ModeUtil.getStringConverter( settings.availableModes() ) ) );

		return grid;
	}

	private static void addBooleanProperty(
			final BooleanProperty property,
			final String name,
			final GridPane grid,
			final int row )
	{
		final CheckBox cb = new CheckBox( name );
		cb.selectedProperty().bindBidirectional( property );
		grid.add( cb, 0, row );
	}

	private static < T > void addSelection(
			final ObservableList< T > choices,
			final ObjectProperty< T > currentChoice,
			final String prompt,
			final GridPane grid,
			final int row,
			final Optional< StringConverter< T > > stringConverter )
	{
		final ComboBox< T > cb = new ComboBox<>( choices );
		cb.valueProperty().bindBidirectional( currentChoice );
		cb.setValue( currentChoice.get() );
		cb.setPromptText( prompt );
		stringConverter.ifPresent( conv -> cb.setConverter( conv ) );
		grid.add( cb, 0, row );
	}

}
