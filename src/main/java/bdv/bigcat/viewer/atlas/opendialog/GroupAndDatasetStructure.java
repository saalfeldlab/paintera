package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Optional;
import java.util.function.BiFunction;

import javafx.beans.binding.Bindings;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.effect.Effect;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class GroupAndDatasetStructure
{

	private final String groupPromptText;

	private final String datasetPromptText;

	private final Property< String > group;

	private final Property< String > dataset;

	private final ObservableList< String > datasetChoices;

	private final ObservableValue< Boolean > isDropDownReady;

	private final BiFunction< String, Scene, String > onBrowseClicked;

	private final SimpleObjectProperty< Effect > groupErrorEffect = new SimpleObjectProperty<>();

	private final Effect textFieldNoErrorEffect = new TextField().getEffect();

	public GroupAndDatasetStructure(
			final String groupPromptText,
			final String datasetPromptText,
			final Property< String > group,
			final Property< String > dataset,
			final ObservableList< String > datasetChoices,
			final ObservableValue< Boolean > isDropDownReady,
			final BiFunction< String, Scene, String > onBrowseClicked )
	{
		super();
		this.groupPromptText = groupPromptText;
		this.datasetPromptText = datasetPromptText;
		this.group = group;
		this.dataset = dataset;
		this.datasetChoices = datasetChoices;
		this.isDropDownReady = isDropDownReady;
		this.onBrowseClicked = onBrowseClicked;
	}

	public Node createNode()
	{
		final TextField groupField = new TextField( group.getValue() );
		groupField.setMinWidth( 0 );
		groupField.setMaxWidth( Double.POSITIVE_INFINITY );
		groupField.setPromptText( groupPromptText );
		groupField.textProperty().bindBidirectional( group );
		final ComboBox< String > datasetDropDown = new ComboBox<>( datasetChoices );
		datasetDropDown.setPromptText( datasetPromptText );
		datasetDropDown.setEditable( false );
		datasetDropDown.valueProperty().bindBidirectional( dataset );
		datasetDropDown.setMinWidth( groupField.getMinWidth() );
		datasetDropDown.setPrefWidth( groupField.getPrefWidth() );
		datasetDropDown.setMaxWidth( groupField.getMaxWidth() );
		datasetDropDown.disableProperty().bind( this.isDropDownReady );
		final GridPane grid = new GridPane();
		grid.add( groupField, 0, 0 );
		grid.add( datasetDropDown, 0, 1 );
		GridPane.setHgrow( groupField, Priority.ALWAYS );
		GridPane.setHgrow( datasetDropDown, Priority.ALWAYS );
		final Button button = new Button( "Browse" );
		button.setOnAction( event -> {
			Optional.ofNullable( onBrowseClicked.apply( group.getValue(), grid.getScene() ) ).ifPresent( group::setValue );
		} );
		grid.add( button, 1, 0 );

		groupField.effectProperty().bind(
				Bindings.createObjectBinding(
						() -> groupField.isFocused() ? this.textFieldNoErrorEffect : groupErrorEffect.get(),
						groupErrorEffect,
						groupField.focusedProperty() ) );

		return grid;
	}

}
