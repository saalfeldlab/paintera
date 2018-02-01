package bdv.bigcat.viewer.atlas.opendialog;

import java.util.stream.Stream;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class DatasetInfo
{

	private final DoubleProperty[] spatialResolution = Stream.generate( SimpleDoubleProperty::new ).limit( 3 ).toArray( DoubleProperty[]::new );

	private final DoubleProperty[] spatialOffset = Stream.generate( SimpleDoubleProperty::new ).limit( 3 ).toArray( DoubleProperty[]::new );

	private final SimpleDoubleProperty min = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty max = new SimpleDoubleProperty( Double.NaN );

	public DoubleProperty[] spatialResolutionProperties()
	{
		return this.spatialResolution;
	}

	public DoubleProperty[] spatialOffsetProperties()
	{
		return this.spatialOffset;
	}

	public DoubleProperty minProperty()
	{
		return this.min;
	}

	public DoubleProperty maxProperty()
	{
		return this.max;
	}

}
