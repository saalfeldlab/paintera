package bdv.bigcat.viewer.atlas.source;

import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

public class NumericSliderWithField
{

	private final Slider slider;

	private final TextField field;

	public NumericSliderWithField(
			final double min, final double max, final double initialValue )
	{
		this( min, max, initialValue, 2 );
	}

	public NumericSliderWithField(
			final double min, final double max, final double initialValue, final int numDecimals )
	{

		assert initialValue >= min;
		assert initialValue <= max;

		this.slider = new Slider( min, max, initialValue );
		this.field = new TextField( Double.toString( initialValue ) );

		this.slider.setShowTickLabels( true );

		final TextFormatter< Double > formatter = DoubleStringFormatter.createFormatter(
				slider.minProperty(),
				slider.maxProperty(),
				initialValue,
				numDecimals );
		this.field.setTextFormatter( formatter );
		formatter.valueProperty().addListener( ( obs, oldv, newv ) -> this.slider.setValue( newv ) );
		this.slider.valueProperty().addListener( ( obs, oldv, newv ) -> formatter.setValue( newv.doubleValue() ) );
//		this.field.textProperty().bindBidirectional( slider.valueProperty(), new NumberStringConverter() );

	}

	public Slider slider()
	{
		return this.slider;
	}

	public TextField textField()
	{
		return this.field;
	}

}
