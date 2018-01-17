package bdv.bigcat.viewer.atlas.source;

import javafx.beans.value.ObservableDoubleValue;
import javafx.util.StringConverter;

public class DoubleStringConverter extends StringConverter< Double >
{

	private String format;

	private final ObservableDoubleValue min;

	private final ObservableDoubleValue max;

	public DoubleStringConverter( final int numDecimals, final ObservableDoubleValue min, final ObservableDoubleValue max )
	{
		this.min = min;
		this.max = max;
		setNumDecimals( numDecimals );
	}

	public void setNumDecimals( final int numDecimals )
	{
		this.format = String.format( "%s%df", "%.", numDecimals );
	}

	@Override
	public Double fromString( final String s )
	{
		if ( s.isEmpty() || "-".equals( s ) || ".".equals( s ) || "-.".equals( s ) )
			return Math.min( Math.max( 0.0, min.get() ), max.get() );
		else
			return Math.min( Math.max( Double.valueOf( s ), min.get() ), max.get() );
	}

	@Override
	public String toString( final Double d )
	{
		return String.format( format, d );
	}

}
