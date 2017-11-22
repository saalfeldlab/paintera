package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.util.volatiles.SharedQueue;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public interface BackendDialog
{

	public Node getDialogNode();

	public ObjectProperty< String > errorMessage();

	public default < T extends RealType< T > & NativeType< T >, V extends RealType< V > > Optional< DataSource< T, V > > getRaw( final String name, final SharedQueue sharedQueue, final int priority ) throws IOException
	{
		return Optional.empty();
	}

	public default Optional< DataSource< ?, ? > > getLabels( final String name )
	{
		return Optional.empty();
	}

//	TODO
//	public DataSource< ?, ? > getChannels();

}
