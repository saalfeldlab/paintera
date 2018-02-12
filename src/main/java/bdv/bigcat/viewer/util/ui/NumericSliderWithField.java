package bdv.bigcat.viewer.util.ui;

import bdv.bigcat.viewer.atlas.ui.DoubleStringFormatter;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

public class NumericSliderWithField
{

	private final Slider slider;

	private final TextField field;

	public NumericSliderWithField(
			final int min,
			final int max,
			final int initialValue )
	{
		this( min, max, initialValue, 0, true );
	}

	public NumericSliderWithField(
			final double min,
			final double max,
			final double initialValue )
	{
		this( min, max, initialValue, 2, false );
	}

	public NumericSliderWithField(
			final double min,
			final double max,
			final double initialValue,
			final int numDecimals,
			final boolean isInteger )
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
		if ( isInteger )
			this.slider.valueProperty().addListener( ( obs, oldv, newv ) -> this.slider.setValue( Math.round( newv.doubleValue() ) ) );

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
