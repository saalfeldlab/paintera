package bdv.bigcat.viewer.atlas.ui.source.converter;

import bdv.bigcat.viewer.atlas.CurrentModeConverter;
import bdv.bigcat.viewer.atlas.ui.BindUnbindAndNodeSupplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;

public class CurrentModeConverterNode implements BindUnbindAndNodeSupplier
{

	private final CurrentModeConverter< ?, ? > converter;

	private final DoubleProperty alpha = new SimpleDoubleProperty();

	private final DoubleProperty activeFragmentAlpha = new SimpleDoubleProperty();

	private final DoubleProperty activeSegmentAlpha = new SimpleDoubleProperty();

	private final BooleanProperty colorFromSegment = new SimpleBooleanProperty();

	public CurrentModeConverterNode( final CurrentModeConverter< ?, ? > converter )
	{
		super();
		this.converter = converter;
	}

	@Override
	public Node get()
	{
		return createNode();
	}

	@Override
	public void bind()
	{
		alpha.bindBidirectional( converter.alphaProperty() );
		activeFragmentAlpha.bindBidirectional( converter.activeFragmentAlphaProperty() );
		activeSegmentAlpha.bindBidirectional( converter.activeSegmentAlphaProperty() );
		colorFromSegment.bindBidirectional( converter.colorFromSegmentIdProperty() );
	}

	@Override
	public void unbind()
	{
		alpha.unbindBidirectional( converter.alphaProperty() );
		activeFragmentAlpha.unbindBidirectional( converter.activeFragmentAlphaProperty() );
		activeSegmentAlpha.unbindBidirectional( converter.activeSegmentAlphaProperty() );
		colorFromSegment.unbindBidirectional( converter.colorFromSegmentIdProperty() );
	}

	private Node createNode()
	{
		final VBox contents = new VBox();
		final GridPane gp = new GridPane();
		final ColumnConstraints secondColumnConstraints = new ColumnConstraints();
		secondColumnConstraints.setMaxWidth( Double.MAX_VALUE );
		secondColumnConstraints.setHgrow( Priority.ALWAYS );
		gp.getColumnConstraints().addAll( secondColumnConstraints );

		final int textFieldWidth = 60;
		int row = 0;

		contents.getChildren().add( gp );

		{
			final Slider alphaSlider = new Slider( 0, 1, alpha.get() );
			alphaSlider.valueProperty().bindBidirectional( alpha );
			alphaSlider.setShowTickLabels( true );
			alphaSlider.setTooltip( new Tooltip( "Alpha for inactive fragments." ) );
			final TextField alphaField = new TextField();
			alphaField.textProperty().bindBidirectional( alphaSlider.valueProperty(), new NumberStringConverter() );
			alphaField.setMinWidth( textFieldWidth );
			alphaField.setMaxWidth( textFieldWidth );
			gp.add( alphaSlider, 0, row );
			gp.add( alphaField, 1, row );
			++row;
		}

		{
			final Slider selectedFragmentAlphaSlider = new Slider( 0, 1, activeFragmentAlpha.get() );
			selectedFragmentAlphaSlider.valueProperty().bindBidirectional( activeFragmentAlpha );
			selectedFragmentAlphaSlider.setShowTickLabels( true );
			selectedFragmentAlphaSlider.setTooltip( new Tooltip( "Alpha for selected fragments." ) );
			final TextField selectedFragmentAlphaField = new TextField();
			selectedFragmentAlphaField.textProperty().bindBidirectional( selectedFragmentAlphaSlider.valueProperty(), new NumberStringConverter() );
			selectedFragmentAlphaField.setMinWidth( textFieldWidth );
			selectedFragmentAlphaField.setMaxWidth( textFieldWidth );
			gp.add( selectedFragmentAlphaSlider, 0, row );
			gp.add( selectedFragmentAlphaField, 1, row );
			++row;
		}

		{
			final Slider selectedSegmentAlphaSlider = new Slider( 0, 1, activeSegmentAlpha.get() );
			selectedSegmentAlphaSlider.valueProperty().bindBidirectional( activeSegmentAlpha );
			selectedSegmentAlphaSlider.setShowTickLabels( true );
			selectedSegmentAlphaSlider.setTooltip( new Tooltip( "Alpha for active segments." ) );
			final TextField selectedSegmentAlphaField = new TextField();
			selectedSegmentAlphaField.textProperty().bindBidirectional( selectedSegmentAlphaSlider.valueProperty(), new NumberStringConverter() );
			selectedSegmentAlphaField.setMinWidth( textFieldWidth );
			selectedSegmentAlphaField.setMaxWidth( textFieldWidth );
			gp.add( selectedSegmentAlphaSlider, 0, row );
			gp.add( selectedSegmentAlphaField, 1, row );
			++row;
		}

		{
			final CheckBox colorFromSegmentId = new CheckBox( "Color From segment Id." );
			colorFromSegmentId.setTooltip( new Tooltip( "Generate fragment color from segment id (on) or fragment id (off)" ) );
			colorFromSegmentId.selectedProperty().bindBidirectional( colorFromSegment );
			contents.getChildren().add( colorFromSegmentId );
		}

//		{
//			if ( state.selectedIdsProperty().get() != null )
//			{
//				final SelectedIdsTextField selectedIdsField = new SelectedIdsTextField( s.selectedIdsProperty().get() );
//				contents.getChildren().add( selectedIdsField.textField() );
//			}
//		}

		return contents;
	}

}
