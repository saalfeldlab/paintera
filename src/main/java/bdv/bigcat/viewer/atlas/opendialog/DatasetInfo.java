package bdv.bigcat.viewer.atlas.opendialog;

import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableIntegerValue;

public class DatasetInfo
{

	private final ObjectProperty< AxisOrder > defaultAxisOrder = new SimpleObjectProperty<>();

	private final ObjectProperty< AxisOrder > selectedAxisOrder = new SimpleObjectProperty<>();

	private final DoubleInfoFromData resolution = new DoubleInfoFromData();

	private final DoubleInfoFromData offset = new DoubleInfoFromData();

	private final DoubleProperty[] spatialResolution = Stream.generate( SimpleDoubleProperty::new ).limit( 3 ).toArray( DoubleProperty[]::new );

	private final DoubleProperty[] spatialOffset = Stream.generate( SimpleDoubleProperty::new ).limit( 3 ).toArray( DoubleProperty[]::new );

	private final SimpleDoubleProperty min = new SimpleDoubleProperty( Double.NaN );

	private final SimpleDoubleProperty max = new SimpleDoubleProperty( Double.NaN );

	private final IntegerBinding numDimensions = Bindings.createIntegerBinding( () -> defaultAxisOrder.get().numDimensions(), defaultAxisOrder );

	public DatasetInfo()
	{
		selectedAxisOrder.addListener( ( obs, oldv, newv ) -> {
			if ( newv != null )
			{
				resolution.bind( newv.permutation(), spatialResolution );
				offset.bind( newv.permutation(), spatialOffset );
			}
		} );
	}

	public ObjectProperty< AxisOrder > defaultAxisOrderProperty()
	{
		return this.defaultAxisOrder;
	}

	public ObjectProperty< AxisOrder > selectedAxisOrderProperty()
	{
		return this.selectedAxisOrder;
	}

	public void setResolution( final double[] resolution )
	{
		this.resolution.set( resolution );
	}

	public void setOffset( final double[] resolution )
	{
		this.offset.set( resolution );
	}

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

	public ObservableIntegerValue numDimensions()
	{
		return numDimensions;
	}

}
