package org.janelia.saalfeldlab.fx.ui;

import java.io.File;
import java.util.Arrays;
import java.util.function.Predicate;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

public class ObjectField< T, P extends Property< T > >
{

	public enum SubmitOn {
		ENTER_PRESSED,
		FOCUS_LOST
	}

	public static class InvalidUserInput extends RuntimeException
	{
		public InvalidUserInput(String message )
		{
			this(message, null);
		}

		public InvalidUserInput(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	private final P value;

	private final TextField textField = new TextField();

	private final StringConverter< T > converter;

	private final EnterPressedHandler enterPressedHandler = new EnterPressedHandler();
	private final FocusLostHandler focusLostHandler = new FocusLostHandler();

	public ObjectField(
			P value,
			StringConverter< T > converter,
			SubmitOn... submitOn)
	{
		this.value = value;
		this.converter = converter;
		value.addListener((obs, oldv, newv) -> {
			textField.setText(this.converter.toString(newv));
		});
		textField.setText(this.converter.toString(this.value.getValue()));
		Arrays.stream(submitOn).forEach(this::enableSubmitOn);
	}

	public P valueProperty()
	{
		return value;
	}

	public TextField textField()
	{
		return textField;
	}

	public void enableSubmitOn(SubmitOn submitOn)
	{
		switch(submitOn)
		{
			case ENTER_PRESSED:
				textField.addEventHandler(KeyEvent.KEY_PRESSED, enterPressedHandler);
				break;
			case FOCUS_LOST:
				textField.focusedProperty().addListener(focusLostHandler);
		}
	}

	public void disableSubmitOn(SubmitOn submitOn)
	{
		switch(submitOn)
		{
			case ENTER_PRESSED:
				textField.removeEventHandler(KeyEvent.KEY_PRESSED, enterPressedHandler);
				break;
			case FOCUS_LOST:
				textField.focusedProperty().removeListener(focusLostHandler);
		}
	}

	public void submit()
	{
		try
		{
			value.setValue(converter.fromString(textField.getText()));
		}
		catch (InvalidUserInput e)
		{
			textField.setText(converter.toString(value.getValue()));
		}
	}

	public static ObjectField<File, Property<File>> fileField(
			File initialFile,
			final Predicate<File> test,
			SubmitOn... submitOn)
	{
		StringConverter<File> converter = new StringConverter<File>() {
			@Override
			public String toString(File file)
			{
				return file.getAbsolutePath();
			}

			@Override
			public File fromString(String s)
			{
				File f = new File(s);
				if (!test.test(f))
				{
					throw new InvalidUserInput("User input could not converted to file: " + s, null);
				}
				return f;
			}
		};
		return new ObjectField<>(new SimpleObjectProperty<>(initialFile), converter, submitOn);
	}

	public static ObjectField<String, StringProperty> stringField(
			String initialValue,
			SubmitOn... submitOn)
	{

		final StringConverter<String> converter = new StringConverter<String>() {
			@Override
			public String toString(String s)
			{
				return s;
			}

			@Override
			public String fromString(String s)
			{
				return s;
			}
		};

		return new ObjectField<>(new SimpleStringProperty(initialValue), converter, submitOn);
	}

	private class EnterPressedHandler implements EventHandler<KeyEvent>
	{
		@Override
		public void handle(KeyEvent e)
		{
			if (e.getCode().equals(KeyCode.ENTER))
			{
				e.consume();
				submit();
			}
		}
	}

	private class FocusLostHandler implements ChangeListener<Boolean>
	{

		@Override
		public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldv, Boolean newv)
		{
			if (!newv)
				submit();
		}
	}
}
