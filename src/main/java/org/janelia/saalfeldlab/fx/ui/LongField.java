package org.janelia.saalfeldlab.fx.ui;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

public class LongField
{
	private final TextField field = new TextField();

	private final StringProperty valueAsString = new SimpleStringProperty();

	private final LongProperty value = new SimpleLongProperty();

	public LongField(final long initialValue)
	{

		valueAsString.addListener((obs, oldv, newv) -> this.field.setText(newv));

		valueAsString.bindBidirectional(value, new StringConverter<Number>()
		{
			@Override
			public String toString(final Number value)
			{
				return value.toString();
			}

			@Override
			public Long fromString(final String string)
			{
				try
				{
					return Long.valueOf(string);
				} catch (NumberFormatException | NullPointerException e)
				{
					return value.get();
				}
			}
		});

		this.value.set(initialValue);

		this.field.setOnAction(wrapAsEventHandler(this::submitText));
		this.field.focusedProperty().addListener((obs, oldv, newv) -> submitText(!newv));

	}

	public TextField textField()
	{
		return this.field;
	}

	public LongProperty valueProperty()
	{
		return this.value;
	}

	private static <E extends Event> EventHandler<E> wrapAsEventHandler(final Runnable r)
	{
		return wrapAsEventHandler(r, true);
	}

	private static <E extends Event> EventHandler<E> wrapAsEventHandler(final Runnable r, final boolean consume)
	{
		return e -> {
			if (consume)
			{
				e.consume();
			}
			r.run();
		};
	}

	private void submitText()
	{
		submitText(true);
	}

	private void submitText(final boolean submit)
	{
		if (!submit) { return; }
		try
		{
			final long val = Long.valueOf(textField().getText());
			value.set(val);
		} catch (NumberFormatException | NullPointerException e)
		{
			this.field.setText(this.valueAsString.get());
		}
	}
}
