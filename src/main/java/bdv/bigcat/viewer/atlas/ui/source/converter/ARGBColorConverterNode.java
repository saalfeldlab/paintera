package bdv.bigcat.viewer.atlas.ui.source.converter;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.ARGBColorConverter;
import bdv.bigcat.viewer.atlas.ui.BindUnbindAndNodeSupplier;
import bdv.bigcat.viewer.atlas.ui.DoubleStringFormatter;
import bdv.bigcat.viewer.util.Colors;
import bdv.bigcat.viewer.util.ui.NumericSliderWithField;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import net.imglib2.type.numeric.ARGBType;

public class ARGBColorConverterNode implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private final ARGBColorConverter< ? > converter;

	private final ObjectProperty< Color > colorProperty = new SimpleObjectProperty<>( Color.WHITE );

	private final ObjectProperty< ARGBType > argbProperty = new SimpleObjectProperty<>( Colors.toARGBType( Color.WHITE ) );

	private final DoubleProperty alphaProperty = new SimpleDoubleProperty();

	private final DoubleProperty min = new SimpleDoubleProperty();

	private final DoubleProperty max = new SimpleDoubleProperty();

	public ARGBColorConverterNode( final ARGBColorConverter< ? > converter )
	{
		super();
		this.converter = converter;
		this.colorProperty.addListener( ( obs, oldv, newv ) -> argbProperty.set( Colors.toARGBType( newv ) ) );
		this.argbProperty.addListener( ( obs, oldv, newv ) -> colorProperty.set( Colors.toColor( newv ) ) );
	}

	@Override
	public Node get()
	{
		return getNodeForARGBColorConverter();
	}

	@Override
	public void bind()
	{
		argbProperty.bindBidirectional( converter.colorProperty() );
		alphaProperty.bindBidirectional( converter.alphaProperty() );
		this.min.bindBidirectional( converter.minProperty() );
		this.max.bindBidirectional( converter.maxProperty() );
	}

	@Override
	public void unbind()
	{
		argbProperty.unbindBidirectional( converter.colorProperty() );
		alphaProperty.unbindBidirectional( converter.alphaProperty() );
		this.min.unbindBidirectional( converter.minProperty() );
		this.max.unbindBidirectional( converter.maxProperty() );
	}

	private Node getNodeForARGBColorConverter()
	{

		final TilePane tilePane = new TilePane( Orientation.VERTICAL );
		tilePane.setMinWidth( 0 );

		final ColorPicker picker = new ColorPicker( Colors.toColor( argbProperty.get() ) );
		picker.valueProperty().bindBidirectional( this.colorProperty );
		final HBox colorPickerBox = new HBox( picker );
		HBox.setHgrow( picker, Priority.ALWAYS );
		tilePane.getChildren().add( colorPickerBox );

		final StringBinding min = this.min.asString();
		final StringBinding max = this.max.asString();
		final TextField minInput = new TextField( min.get() );
		final TextField maxInput = new TextField( max.get() );
		minInput.promptTextProperty().bind( this.min.asString( "min=%f" ) );
		minInput.promptTextProperty().bind( this.max.asString( "max=%f" ) );

		min.addListener( ( obs, oldv, newv ) -> minInput.setText( newv ) );
		max.addListener( ( obs, oldv, newv ) -> maxInput.setText( newv ) );

		final TextFormatter< Double > minFormatter = DoubleStringFormatter.createFormatter( this.min.get(), 2 );
		final TextFormatter< Double > maxFormatter = DoubleStringFormatter.createFormatter( this.max.get(), 2 );

		minInput.setTextFormatter( minFormatter );
		maxInput.setTextFormatter( maxFormatter );

		minInput.setOnKeyPressed( event -> {
			if ( event.getCode().equals( KeyCode.ENTER ) )
			{
				minInput.commitValue();
				event.consume();
			}
		} );
		maxInput.setOnKeyPressed( event -> {
			if ( event.getCode().equals( KeyCode.ENTER ) )
			{
				maxInput.commitValue();
				event.consume();
			}
		} );

		minInput.setTooltip( new Tooltip( "min" ) );
		maxInput.setTooltip( new Tooltip( "max" ) );

		minFormatter.valueProperty().addListener( ( obs, oldv, newv ) -> this.min.set( newv ) );
		maxFormatter.valueProperty().addListener( ( obs, oldv, newv ) -> this.max.set( newv ) );

		this.min.addListener( ( obs, oldv, newv ) -> minFormatter.setValue( newv.doubleValue() ) );
		this.max.addListener( ( obs, oldv, newv ) -> maxFormatter.setValue( newv.doubleValue() ) );

		final HBox minMaxBox = new HBox( minInput, maxInput );
		tilePane.getChildren().add( minMaxBox );

		final NumericSliderWithField alphaSliderWithField = new NumericSliderWithField( 0, 1, this.alphaProperty.get() );
		alphaSliderWithField.slider().valueProperty().bindBidirectional( this.alphaProperty );
		alphaSliderWithField.textField().setMinWidth( 48 );
		alphaSliderWithField.textField().setMaxWidth( 48 );
		final HBox alphaBox = new HBox( alphaSliderWithField.slider(), alphaSliderWithField.textField() );
		Tooltip.install( alphaSliderWithField.slider(), new Tooltip( "alpha" ) );
		HBox.setHgrow( alphaSliderWithField.slider(), Priority.ALWAYS );
		tilePane.getChildren().add( alphaBox );

		LOG.debug( "Returning TilePane with children: ", tilePane.getChildren() );

		return tilePane;
	}

}
