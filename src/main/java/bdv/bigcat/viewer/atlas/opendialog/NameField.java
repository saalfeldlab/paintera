package bdv.bigcat.viewer.atlas.opendialog;

import java.util.Optional;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;

public class NameField
{

	private final SimpleObjectProperty< String > errorMessage;

	private final String errorString;

	private final TextField nameField;

	public NameField( final String name, final String prompt )
	{
		this.nameField = new TextField( "" );
		this.nameField.setPromptText( prompt );
		this.errorString = name + " not specified!";
		this.errorMessage = new SimpleObjectProperty<>( errorString );
		this.nameField.textProperty().addListener( ( obs, oldv, newv ) -> this.errorMessage.set( Optional.ofNullable( newv ).orElse( "" ).length() > 0 ? "" : errorString ) );
	}

	public ObservableValue< String > errorMessageProperty()
	{
		return this.errorMessage;
	}

	public TextField textField()
	{
		return this.nameField;
	}

	public String getText()
	{
		return this.nameField.getText();
	}

}
