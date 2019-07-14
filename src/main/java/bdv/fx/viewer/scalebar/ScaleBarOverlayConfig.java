package bdv.fx.viewer.scalebar;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import tech.units.indriya.unit.Units;

import javax.measure.Unit;
import javax.measure.quantity.Length;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static javax.measure.MetricPrefix.KILO;
import static javax.measure.MetricPrefix.MICRO;
import static javax.measure.MetricPrefix.MILLI;
import static javax.measure.MetricPrefix.NANO;

public class ScaleBarOverlayConfig {

	private static final List<Unit<Length>> UNITS = Arrays.asList(
			NANO(Units.METRE),
			MICRO (Units.METRE),
			MILLI (Units.METRE),
			Units.METRE,
			KILO(Units.METRE));

	public static List<Unit<Length>> units() {
		return Collections.unmodifiableList(UNITS);
	}

	private final ObjectProperty<Color> foregroundColor = new SimpleObjectProperty<>(Color.web("#FFFFFFFF"));

	private final ObjectProperty<Color> backgroundColor = new SimpleObjectProperty<>(Color.web("#00000088"));

	private final IntegerProperty numDecimals = new SimpleIntegerProperty(4);

	private final ObjectProperty<Font> overlayFont = new SimpleObjectProperty<>(new Font("SansSerif", 18.0));

	private final BooleanProperty isShowing = new SimpleBooleanProperty(true);

	private final DoubleProperty targetScaleBarLength = new SimpleDoubleProperty(200.0);

	private final ObjectProperty<Unit<Length>> baseUnit = new SimpleObjectProperty<>(NANO(Units.METRE));

	private final BooleanProperty change = new SimpleBooleanProperty(false);
	{
		final InvalidationListener listener = obs -> change.set(!change.get());
		foregroundColor.addListener(listener);
		backgroundColor.addListener(listener);
		numDecimals.addListener(listener);
		overlayFont.addListener(listener);
		isShowing.addListener(listener);
		targetScaleBarLength.addListener(listener);
		baseUnit.addListener(listener);
	}

//  TODO Why does this not work instead of the setting a BooleanProperty???
//	private final Observable change = Bindings.createObjectBinding(
//			() -> null,
//			foregroundColor,
//			backgroundColor,
//			numDecimals,
//			overlayFont,
//			isShowing,
//			targetScaleBarLength,
//			baseUnit);

	public ObjectProperty<Color> foregroundColorProperty() {
		return this.foregroundColor;
	}

	public Color getForegroundColor() {
		return foregroundColorProperty().get();
	}

	public void setForegroundColor(final Color color) {
		foregroundColorProperty().set(color);
	}

	public ObjectProperty<Color> backgroundColorProperty() {
		return this.backgroundColor;
	}

	public Color getBackgroundColor() {
		return backgroundColorProperty().get();
	}

	public void setBackgroundColor(final Color color) {
		backgroundColorProperty().set(color);
	}

	public IntegerProperty numDecimalsProperty() {
		return this.numDecimals;
	}

	public int getNumDecimals() {
		return numDecimalsProperty().get();
	}

	public void setNumDecimals(final int numDecimals) {
		numDecimalsProperty().set(numDecimals);
	}

	public ObjectProperty<Font> overlayFontProperty() {
		return this.overlayFont;
	}

	public void setOverlayFont(final Font font) {

		overlayFontProperty().set(font);
	}

	public Font getOverlayFont() {
		return overlayFontProperty().get();
	}

	public BooleanProperty isShowingProperty() {
		return this.isShowing;
	}

	public boolean getIsShowing() {
		return isShowingProperty().get();
	}

	public void setIsShowing(final boolean isShowing) {
		isShowingProperty().set(isShowing);
	}

	public DoubleProperty targetScaleBarLengthProperty() {
		return this.targetScaleBarLength;
	}

	public double getTargetScaleBarLength() {
		return targetScaleBarLengthProperty().get();
	}

	public void setTargetScaleBarLength(final double length) {
		targetScaleBarLengthProperty().set(length);
	}

	public ObjectProperty<Unit<Length>> baseUnitProperty() {
		return this.baseUnit;
	}

	public Unit<Length> getBaseUnit() {
		return baseUnitProperty().get();
	}

	public void setBaseUnit(final Unit<Length> unit) {
		baseUnitProperty().set(unit);
	}

	public Observable getChange() {
		return this.change;
	}




}
