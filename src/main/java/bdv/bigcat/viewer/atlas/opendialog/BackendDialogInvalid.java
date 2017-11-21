package bdv.bigcat.viewer.atlas.opendialog;

import bdv.bigcat.viewer.atlas.opendialog.OpenSourceDialog.BACKEND;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

public class BackendDialogInvalid implements BackendDialog
{

	private final OpenSourceDialog.BACKEND backend;

	public BackendDialogInvalid( final BACKEND backend )
	{
		super();
		this.backend = backend;
	}

	@Override
	public Node getDialogNode()
	{
		return new StackPane();
	}

	@Override
	public ObjectProperty< String > errorMessage()
	{
		return new SimpleObjectProperty<>( "Loader for " + backend + " backend not implemented yet." );
	}

}
