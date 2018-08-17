package org.janelia.saalfeldlab.fx.ui;

import java.util.Arrays;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.LongPropertyBase;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ObjectField< T, P extends Property< T > >
{

	public enum SubmitOn {
		ENTER_PRESSED,
		FOCUS_LOST
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
		catch (Exception e)
		{
			textField.setText(converter.toString(value.getValue()));
		}
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
