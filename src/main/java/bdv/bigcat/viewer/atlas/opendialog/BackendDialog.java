package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog.TYPE;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;

public interface BackendDialog
{

	public Node getDialogNode();

	public ObjectProperty< String > errorMessage();

	public default < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > Collection< DataSource< T, V > > getRaw(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return new ArrayList<>();
	}

	public default Collection< LabelDataSource< ?, ? > > getLabels(
			final String name,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		return new ArrayList<>();
	}

	public default DoubleProperty resolutionX()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty resolutionY()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty resolutionZ()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty offsetX()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty offsetY()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty offsetZ()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty min()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty max()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default void typeChanged( final TYPE type )
	{}

	public default IntegerProperty numDimensions()
	{
		return new SimpleIntegerProperty( 3 );
	}

	public default ObjectProperty< AxisOrder > axisOrder()
	{
		return new SimpleObjectProperty<>( AxisOrder.defaultOrder( numDimensions().get() ).orElse( null ) );
	}

//	TODO
//	public DataSource< ?, ? > getChannels();

}
