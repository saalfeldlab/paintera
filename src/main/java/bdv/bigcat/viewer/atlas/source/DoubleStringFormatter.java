package bdv.bigcat.viewer.atlas.source;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.TextFormatter;

public class DoubleStringFormatter
{

	public static TextFormatter< Double > createFormatter(
			final double initialValue,
			final int numDecimals )
	{
		return createFormatter( new SimpleDoubleProperty( Double.NEGATIVE_INFINITY ), new SimpleDoubleProperty( Double.POSITIVE_INFINITY ), initialValue, numDecimals );
	}

	public static TextFormatter< Double > createFormatter(
			final DoubleProperty min,
			final DoubleProperty max,
			final double initialValue,
			final int numDecimals )
	{
		final DoubleFilter filter = new DoubleFilter();
		final DoubleStringConverter converter = new DoubleStringConverter( numDecimals, min, max );
		return new TextFormatter<>( converter, initialValue, filter );
	}

}
