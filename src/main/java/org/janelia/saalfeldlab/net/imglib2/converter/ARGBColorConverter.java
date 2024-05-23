package org.janelia.saalfeldlab.net.imglib2.converter;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public abstract class ARGBColorConverter<R extends RealType<R>> implements ColorConverter, Converter<R, ARGBType> {

	/* Reading from properties is slow; use these, they are updated by a listener to the respective properties */
	private double alpha = 1.0;
	private double min = 0.0;
	private double max = 1.0;

	private int color = ARGBType.rgba( 255, 255, 255, 255 );

	protected final DoubleProperty alphaProperty = new SimpleDoubleProperty(alpha);
	protected final DoubleProperty minProperty = new SimpleDoubleProperty(min);
	protected final DoubleProperty maxProperty = new SimpleDoubleProperty(max);
	protected final ObjectProperty<ARGBType> colorProperty = new SimpleObjectProperty<>(new ARGBType(color));

	protected int A;

	protected double scaleR;

	protected double scaleG;

	protected double scaleB;

	protected int black;

	public ARGBColorConverter() {

		this(0, 255);
	}

	public ARGBColorConverter(final double min, final double max) {

		this.min = min;
		this.max = max;
		this.minProperty.set(min);
		this.maxProperty.set(max);

		this.minProperty.addListener((obs, oldv, newv) -> {
			this.min = newv.doubleValue();
			update();
		});
		this.maxProperty.addListener((obs, oldv, newv) ->  {
			this.max = newv.doubleValue();
			update();
		});
		this.alphaProperty.addListener((obs, oldv, newv) ->  {
			this.alpha = newv.doubleValue();
			update();
		});
		this.colorProperty.addListener((obs, oldv, newv) -> {
			this.color = newv.get();
			update();
		});

		update();
	}

	public DoubleProperty minProperty() {

		return minProperty;
	}

	public DoubleProperty maxProperty() {

		return maxProperty;
	}

	public ObjectProperty<ARGBType> colorProperty() {

		return colorProperty;
	}

	public DoubleProperty alphaProperty() {

		return alphaProperty;
	}

	@Override
	public ARGBType getColor() {

		return new ARGBType(color);
	}

	@Override
	public void setColor(final ARGBType c) {

		colorProperty.set(c);
	}

	@Override
	public boolean supportsColor() {

		return true;
	}

	@Override
	public double getMin() {

		return min;
	}

	@Override
	public double getMax() {

		return max;
	}

	@Override
	public void setMax(final double max) {

		this.maxProperty.set(max);
	}

	@Override
	public void setMin(final double min) {

		this.minProperty.set(min);
	}

	private void update() {

		final double scale = 1.0 / (max - min);
		final int value = color;
		A = (int)Math.min(Math.max(Math.round(255 * alpha), 0), 255);
		scaleR = ARGBType.red(value) * scale;
		scaleG = ARGBType.green(value) * scale;
		scaleB = ARGBType.blue(value) * scale;
		black = ARGBType.rgba(0, 0, 0, A);
	}

	public static class Imp0<R extends RealType<R>> extends ARGBColorConverter<R> {

		public Imp0() {

			super();
		}

		public Imp0(final double min, final double max) {

			super(min, max);
		}

		@Override
		public void convert(final R input, final ARGBType output) {

			final double v = input.getRealDouble() - getMin();
			if (v < 0) {
				output.set(black);
			} else {
				final int r0 = (int)(scaleR * v + 0.5);
				final int g0 = (int)(scaleG * v + 0.5);
				final int b0 = (int)(scaleB * v + 0.5);
				final int r = Math.min(255, r0);
				final int g = Math.min(255, g0);
				final int b = Math.min(255, b0);
				output.set(ARGBType.rgba(r, g, b, A));
			}
		}
	}

	public static class Imp1<R extends RealType<R>> extends ARGBColorConverter<R> {

		public Imp1() {

			super();
		}

		public Imp1(final double min, final double max) {

			super(min, max);
		}

		@Override
		public void convert(final R input, final ARGBType output) {

			final double v = input.getRealDouble() - getMin();
			if (v < 0) {
				output.set(black);
			} else {
				final int r0 = (int)(scaleR * v + 0.5);
				final int g0 = (int)(scaleG * v + 0.5);
				final int b0 = (int)(scaleB * v + 0.5);
				final int r = Math.min(255, r0);
				final int g = Math.min(255, g0);
				final int b = Math.min(255, b0);
				output.set(ARGBType.rgba(r, g, b, A));
			}
		}
	}

	public static class InvertingImp0<R extends RealType<R>> extends ARGBColorConverter<R> {

		public InvertingImp0() {

			super();
		}

		public InvertingImp0(final double min, final double max) {

			super(min, max);
		}

		@Override
		public void convert(final R input, final ARGBType output) {

			final double v = input.getRealDouble() - getMin();
			final int r0 = (int)(scaleR * v + 0.5);
			final int g0 = (int)(scaleG * v + 0.5);
			final int b0 = (int)(scaleB * v + 0.5);
			final int r = Math.min(255, Math.max(r0, 0));
			final int g = Math.min(255, Math.max(g0, 0));
			final int b = Math.min(255, Math.max(b0, 0));
			output.set(ARGBType.rgba(r, g, b, A));
		}

	}

	public static class InvertingImp1<R extends RealType<R>> extends ARGBColorConverter<R> {

		public InvertingImp1() {

			super();
		}

		public InvertingImp1(final double min, final double max) {

			super(min, max);
		}

		@Override
		public void convert(final R input, final ARGBType output) {

			final double v = input.getRealDouble() - getMin();
			final int r0 = (int)(scaleR * v + 0.5);
			final int g0 = (int)(scaleG * v + 0.5);
			final int b0 = (int)(scaleB * v + 0.5);
			final int r = Math.min(255, Math.max(r0, 0));
			final int g = Math.min(255, Math.max(g0, 0));
			final int b = Math.min(255, Math.max(b0, 0));
			output.set(ARGBType.rgba(r, g, b, A));
		}

	}
}
