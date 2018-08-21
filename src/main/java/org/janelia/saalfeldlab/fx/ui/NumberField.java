package org.janelia.saalfeldlab.fx.ui;

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
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class NumberField<P extends Property<Number>> extends ObjectField<Number, P>
{
	public NumberField(P value, StringConverter converter, SubmitOn... submitOn)
	{
		super(value, converter, submitOn);
	}

	public static NumberField<DoubleProperty> doubleField(
			double initialValue,
			final DoublePredicate test,
			SubmitOn... submitOn
	                                                     )
	{
		StringConverter<Number> converter = new StringConverter<Number>()
		{
			@Override
			public String toString(Number number)
			{
				return Double.toString(number.doubleValue());
			}

			@Override
			public Number fromString(String s)
			{
				try
				{
					final double val = Double.parseDouble(s);
					if (!test.test(val))
						throw new ObjectField.InvalidUserInput("Illegal value: " + s);
					return val;
				} catch (NumberFormatException e)
				{
					throw new ObjectField.InvalidUserInput("Unable to convert: " + s, e);
				}
			}
		};

		return new NumberField<>(new SimpleDoubleProperty(initialValue), converter, submitOn);
	}

	public static NumberField<LongProperty> longField(
			long initialValue,
			final LongPredicate test,
			SubmitOn... submitOn
	                                                 )
	{
		StringConverter<Number> converter = new StringConverter<Number>()
		{
			@Override
			public String toString(Number number)
			{
				return Long.toString(number.longValue());
			}

			@Override
			public Number fromString(String s)
			{
				try
				{
					final long val = Long.parseLong(s);
					if (!test.test(val))
						throw new ObjectField.InvalidUserInput("Illegal value: " + s);
					return val;
				} catch (NumberFormatException e)
				{
					throw new ObjectField.InvalidUserInput("Unable to convert: " + s, e);
				}
			}
		};

		LongProperty lp = new LongPropertyBase(initialValue)
		{
			@Override
			public Object getBean()
			{
				return null;
			}

			@Override
			public String getName()
			{
				return "";
			}
		};

		return new NumberField<>(lp, converter, submitOn);
	}

	public static NumberField<IntegerProperty> intField(
			int initialValue,
			final IntPredicate test,
			SubmitOn... submitOn
	                                                   )
	{
		StringConverter<Number> converter = new StringConverter<Number>()
		{
			@Override
			public String toString(Number number)
			{
				return Long.toString(number.intValue());
			}

			@Override
			public Integer fromString(String s)
			{
				try
				{
					final int val = Integer.parseInt(s);
					if (!test.test(val))
						throw new ObjectField.InvalidUserInput("Illegal value: " + s);
					return val;
				}
				catch (NumberFormatException e)
				{
					throw new ObjectField.InvalidUserInput("Unable to convert: " + s, e);
				}
			}
		};

		return new NumberField<>(new SimpleIntegerProperty(initialValue), converter, submitOn);
	}

	public static void main(String[] args)
	{
		PlatformImpl.startup(() -> {
		});

		final ObjectField<Number, DoubleProperty> df = doubleField(
				5.0,
				d -> true,
				SubmitOn.ENTER_PRESSED,
				SubmitOn.FOCUS_LOST
		                                                          );
		final TextField        lbl1       = new TextField();
		final StringExpression converted1 = Bindings.convert(df.valueProperty());
		lbl1.textProperty().bind(converted1);
		final HBox hb1 = new HBox(df.textField(), lbl1);

		final ObjectField<Number, LongProperty> lf = longField(
				4,
				d -> true,
				SubmitOn.ENTER_PRESSED,
				SubmitOn.FOCUS_LOST
		                                                      );
		final TextField        lbl2       = new TextField();
		final StringExpression converted2 = Bindings.convert(lf.valueProperty());
		lbl2.textProperty().bind(converted2);
		final HBox hb2 = new HBox(lf.textField(), lbl2);

		final ObjectField<Number, LongProperty> ulf = longField(
				4,
				d -> d >= 0,
				SubmitOn.ENTER_PRESSED,
				SubmitOn.FOCUS_LOST
		                                                       );
		final TextField        lbl3       = new TextField();
		final StringExpression converted3 = Bindings.convert(ulf.valueProperty());
		lbl3.textProperty().bind(converted3);
		final HBox hb3 = new HBox(ulf.textField(), lbl3);


		Platform.runLater(() -> {
			final VBox  pane  = new VBox(hb1, hb2, hb3);
			final Scene scene = new Scene(pane);
			final Stage stage = new Stage();
			stage.setScene(scene);
			stage.show();
		});
	}
}
