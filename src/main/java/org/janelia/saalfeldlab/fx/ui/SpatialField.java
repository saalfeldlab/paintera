package org.janelia.saalfeldlab.fx.ui;

import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class SpatialField<P extends Property<Number>>
{

	private final NumberField<P> x;

	private final NumberField<P> y;

	private final NumberField<P> z;

	private final Node pane;

	private SpatialField(
			NumberField<P> x,
			NumberField<P> y,
			NumberField<P> z,
			double textFieldWidth)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		x.textField().setPromptText("X");
		y.textField().setPromptText("Y");
		z.textField().setPromptText("Z");
		x.textField().setPrefWidth(textFieldWidth);
		y.textField().setPrefWidth(textFieldWidth);
		z.textField().setPrefWidth(textFieldWidth);
		this.pane = new HBox(x.textField(), y.textField(), z.textField());
	}

	public Node getNode()
	{
		return this.pane;
	}

	public NumberField<P> getX()
	{
		return x;
	}

	public NumberField<P> getY()
	{
		return y;
	}

	public NumberField<P> getZ()
	{
		return z;
	}

	public long[] getAs(long[] xyz)
	{
		xyz[0] = getX().valueProperty().getValue().longValue();
		xyz[1] = getY().valueProperty().getValue().longValue();
		xyz[2] = getZ().valueProperty().getValue().longValue();
		return xyz;
	}

	public int[] getAs(int[] xyz)
	{
		xyz[0] = getX().valueProperty().getValue().intValue();
		xyz[1] = getY().valueProperty().getValue().intValue();
		xyz[2] = getZ().valueProperty().getValue().intValue();
		return xyz;
	}

	public double[] getAs(double[] xyz)
	{
		xyz[0] = getX().valueProperty().getValue().doubleValue();
		xyz[1] = getY().valueProperty().getValue().doubleValue();
		xyz[2] = getZ().valueProperty().getValue().doubleValue();
		return xyz;
	}

	public static SpatialField<DoubleProperty> doubleField(
			final double initialValue,
			final DoublePredicate test,
			final double textFieldWidth,
			final ObjectField.SubmitOn... submitOn)
	{
		return new SpatialField<>(
				NumberField.doubleField(initialValue, test, submitOn),
				NumberField.doubleField(initialValue, test, submitOn),
				NumberField.doubleField(initialValue, test, submitOn),
				textFieldWidth
		);
	}

	public static SpatialField<IntegerProperty> intField(
			final int initialValue,
			final IntPredicate test,
			final double textFieldWidth,
			final ObjectField.SubmitOn... submitOn)
	{
		return new SpatialField<>(
				NumberField.intField(initialValue, test, submitOn),
				NumberField.intField(initialValue, test, submitOn),
				NumberField.intField(initialValue, test, submitOn),
				textFieldWidth
		);
	}

	public static SpatialField<LongProperty> longField(
			final long initialValue,
			final LongPredicate test,
			final double textFieldWidth,
			final ObjectField.SubmitOn... submitOn)
	{
		return new SpatialField<>(
				NumberField.longField(initialValue, test, submitOn),
				NumberField.longField(initialValue, test, submitOn),
				NumberField.longField(initialValue, test, submitOn),
				textFieldWidth
		);
	}
}
