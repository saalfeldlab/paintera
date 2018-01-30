package bdv.bigcat.viewer.atlas;

import java.util.Optional;

import bdv.bigcat.viewer.atlas.mode.ModeUtil;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.util.converter.NumberStringConverter;

public class AtlasSettingsNode
{

	private final AtlasSettings settings;

	public AtlasSettingsNode( final AtlasSettings settings )
	{
		super();
		this.settings = settings;
	}

	public static Node getNode( final AtlasSettings settings, final ObservableDoubleValue width )
	{
		return new AtlasSettingsNode( settings ).getNode( width );
	}

	public Node getNode( final ObservableDoubleValue width )
	{
		final VBox contents = new VBox();

		final VBox checkBoxGrid = new VBox();
		{
			addBooleanProperty( settings.allowRotationsProperty(), "Rotations", checkBoxGrid );
		}

		final GridPane choicesGrid = new GridPane();
		{
			int row = 0;
			addSelection( settings.availableModes(), settings.currentModeProperty(), "Mode", choicesGrid, row++, Optional.of( ModeUtil.getStringConverter( settings.availableModes() ) ) );
		}

		final TitledPane navigationSpeeds = new TitledPane( "Navigation Speed", null );
		{
			int speedRow = 0;
			final GridPane speedGrid = new GridPane();
			addDoubleProperty( "zoom", "zoom speed", settings.zoomSpeedProperty(), 1.05, 10, speedGrid, speedRow++ );
			addDoubleProperty( "rotation", "rotation speed", settings.rotationSpeedProperty(), 0.1, 10, speedGrid, speedRow++ );
			addDoubleProperty( "translation", "translation speed", settings.translationSpeedProperty(), 0.1, 10, speedGrid, speedRow++ );
			navigationSpeeds.setContent( speedGrid );
		}

		contents.getChildren().addAll( checkBoxGrid, choicesGrid, navigationSpeeds );
		contents.maxWidthProperty().bind( width );

		checkBoxGrid.prefWidthProperty().bind( width );
		checkBoxGrid.maxWidthProperty().set( Double.MAX_VALUE );
		checkBoxGrid.minWidthProperty().set( 30 );

		choicesGrid.prefWidthProperty().bind( width );
		choicesGrid.maxWidthProperty().set( Double.MAX_VALUE );
		choicesGrid.minWidthProperty().set( 30 );

		navigationSpeeds.prefWidthProperty().bind( width );
		navigationSpeeds.maxWidthProperty().set( Double.MAX_VALUE );
		navigationSpeeds.minWidthProperty().set( 30 );

		return contents;
	}

	private static void addBooleanProperty(
			final BooleanProperty property,
			final String name,
			final Pane grid )
	{
		final CheckBox cb = new CheckBox();
		cb.selectedProperty().bindBidirectional( property );
		final Label label = new Label( name );
//		final HBox hbox = new HBox( cb, label );
		final AnchorPane anchorPane = new AnchorPane( cb, label );
//		AnchorPane.setLeftAnchor( cb, 0.0 );
		AnchorPane.setRightAnchor( label, 0.0 );
//		HBox.setHgrow( label, Priority.ALWAYS );
		grid.getChildren().add( anchorPane );
	}

	private static void addDoubleProperty(
			final String id,
			final String toolTip,
			final DoubleProperty property,
			final double min,
			final double max,
			final GridPane grid,
			final int row )
	{
		final Slider slider = new Slider( min, max, property.get() );
		slider.valueProperty().bindBidirectional( property );
		slider.setShowTickLabels( true );
		slider.setTooltip( new Tooltip( toolTip ) );
		slider.setMinWidth( 20 );
		final TextField text = new TextField();
		text.setMaxWidth( 40 );
		text.setTooltip( new Tooltip( toolTip ) );
		final Label label = new Label( id );
		text.textProperty().bindBidirectional( slider.valueProperty(), new NumberStringConverter() );
		label.setTooltip( new Tooltip( toolTip ) );
		label.setMaxWidth( 40 );
		final HBox sliderBackground = new HBox( slider );
		grid.add( label, 0, row );
		grid.add( text, 1, row );
		grid.add( sliderBackground, 2, row );
		GridPane.setHgrow( slider, Priority.ALWAYS );
		slider.addEventFilter( MouseEvent.MOUSE_CLICKED, event -> {
			if ( event.getClickCount() == 2 || event.isSecondaryButtonDown() )
			{
				final Dialog< Boolean > d = new Dialog<>();
				d.setResultConverter( ButtonType.OK::equals );
				d.getDialogPane().getButtonTypes().addAll( ButtonType.OK, ButtonType.CANCEL );
				final SimpleDoubleProperty sliderMin = new SimpleDoubleProperty( slider.getMin() );
				final SimpleDoubleProperty sliderMax = new SimpleDoubleProperty( slider.getMax() );
				final TextField minField = new TextField();
				final TextField maxField = new TextField();
				minField.textProperty().bindBidirectional( sliderMin, new NumberStringConverter() );
				maxField.textProperty().bindBidirectional( sliderMax, new NumberStringConverter() );
				minField.setPromptText( "min" );
				maxField.setPromptText( "max" );
				final GridPane minMaxGrid = new GridPane();
				minMaxGrid.add( new Label( "min" ), 0, 0 );
				minMaxGrid.add( new Label( "max" ), 0, 1 );
				minMaxGrid.add( minField, 1, 0 );
				minMaxGrid.add( maxField, 1, 1 );
				final BooleanBinding invalidInput = minField.textProperty().length().isEqualTo( 0 ).or( maxField.textProperty().length().isEqualTo( 0 ) );
				d.getDialogPane().lookupButton( ButtonType.OK ).disableProperty().bind( invalidInput.or( sliderMin.greaterThanOrEqualTo( sliderMax ) ) );
				d.setTitle( String.format( "Set slider range (%s)", id ) );
				d.setGraphic( minMaxGrid );

				final Optional< Boolean > result = d.showAndWait();
				if ( result.isPresent() && result.get() )
				{
					final double m = sliderMin.get();
					final double M = sliderMax.get();
					final double v = M > m ? Math.min( Math.max( slider.getValue(), m ), M ) : Math.min( Math.max( slider.getValue(), M ), m );
					slider.setMin( m );
					slider.setMax( M );
					slider.setValue( v );
				}

				event.consume();
			}
		} );
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
		final Label label = new Label( prompt );
		label.setMinWidth( 40 );
		label.setTooltip( new Tooltip( prompt ) );
		grid.add( label, 0, row );
		grid.add( cb, 1, row );
		GridPane.setHgrow( cb, Priority.ALWAYS );
		cb.setMaxWidth( Double.MAX_VALUE );
	}

}
