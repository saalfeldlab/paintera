package bdv.bigcat.viewer.atlas.mode;

import java.util.Arrays;
import java.util.Collection;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

public class ModeUtil
{

	public static ComboBox< Mode > comboBox( final Mode... modes )
	{
		return comboBox( Arrays.asList( modes ) );
	}

	public static ComboBox< Mode > comboBox( final Collection< Mode > modes )
	{
		return comboBox( FXCollections.observableArrayList( modes ) );
	}

	public static ComboBox< Mode > comboBox( final ObservableList< Mode > modes )
	{
		final ComboBox< Mode > comboBox = new ComboBox<>( modes );
		for ( final Mode mode : modes )
			mode.disable();
		comboBox.setConverter( new StringConverter< Mode >()
		{

			@Override
			public String toString( final Mode object )
			{
				return object == null ? null : object.getName();
			}

			@Override
			public Mode fromString( final String string )
			{
				return comboBox.getItems().stream().filter( mode -> mode.getName().equals( string ) ).findAny().orElse( null );
			}
		} );

		comboBox.valueProperty().addListener( ( ChangeListener< Mode > ) ( observable, oldValue, newValue ) -> {
			if ( oldValue != null && newValue != null )
			{
				oldValue.disable();
				newValue.enable();
			}
		} );

		return comboBox;
	}

	public static StringConverter< Mode > getStringConverter( final ObservableList< Mode > availableModes )
	{
		return new StringConverter< Mode >()
		{

			@Override
			public String toString( final Mode object )
			{
				return object == null ? null : object.getName();
			}

			@Override
			public Mode fromString( final String string )
			{
				return availableModes.stream().filter( mode -> mode.getName().equals( string ) ).findAny().orElse( null );
			}
		};

	}

}
