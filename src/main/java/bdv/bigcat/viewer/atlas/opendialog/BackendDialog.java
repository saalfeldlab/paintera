package bdv.bigcat.viewer.atlas.opendialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.LabelDataSource;
import bdv.util.IdService;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;

public interface BackendDialog
{

	public Node getDialogNode();

	public ObjectProperty< String > errorMessage();

	public default < T extends RealType< T > & NativeType< T >, V extends AbstractVolatileRealType< T, V > & NativeType< V > > Collection< DataSource< T, V > > getRaw(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws Exception
	{
		return new ArrayList<>();
	}

	public default Collection< ? extends LabelDataSource< ?, ? > > getLabels(
			final String name,
			final SharedQueue sharedQueue,
			final int priority ) throws Exception
	{
		return new ArrayList<>();
	}

	public DoubleProperty[] resolution();

	public default void setResolution( final double[] resolution )
	{
		final DoubleProperty[] res = resolution();
		for ( int i = 0; i < res.length; ++i )
			res[ i ].set( resolution[ i ] );
	}

	public DoubleProperty[] offset();

	public default void setOffset( final double[] offset )
	{
		final DoubleProperty[] off = offset();
		for ( int i = 0; i < off.length; ++i )
			off[ i ].set( offset[ i ] );
	}

	public default DoubleProperty min()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default DoubleProperty max()
	{
		return new SimpleDoubleProperty( Double.NaN );
	}

	public default Consumer< RandomAccessibleInterval< UnsignedLongType > > commitCanvas()
	{
		return null;
	}

	public default IdService idService()
	{
		return null;
	}

	public ObservableStringValue nameProperty();

	public String identifier();

}
