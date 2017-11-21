package bdv.bigcat.viewer.atlas.opendialog;

import java.io.IOException;
import java.util.Optional;

import bdv.bigcat.viewer.atlas.data.DataSource;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import net.imglib2.type.numeric.RealType;

public interface BackendDialog
{

	public Node getDialogNode();

	public ObjectProperty< String > errorMessage();

	public default Optional< DataSource< ? extends RealType< ? >, ? extends RealType< ? > > > getRaw( final String name ) throws IOException
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
