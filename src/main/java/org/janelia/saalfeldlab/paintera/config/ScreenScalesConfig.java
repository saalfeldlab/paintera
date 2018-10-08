package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;

public class ScreenScalesConfig {

	private static final double[] DEFAULT_SCREEN_SCALES = new double[] {1.0, 0.5, 0.25, 0.125, 0.0625};

	public static class ScreenScales {

		private final double[] scales;

		public ScreenScales(final ScreenScales scales)
		{
			this(scales.scales.clone());
		}

		public ScreenScales(double... scales) {
			this.scales = scales;
		}

		public double[] getScalesCopy()
		{
			return this.scales.clone();
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode(scales);
		}

		@Override
		public boolean equals(Object other)
		{
			return other != null
					&& other instanceof ScreenScales
					&& Arrays.equals(((ScreenScales) other).scales, this.scales);
		}

		@Override
		public String toString()
		{
			return String.format("{ScreenScales: %s}", Arrays.toString(scales));
		}
	}

	private final ObjectProperty<ScreenScales> screenScales = new SimpleObjectProperty<>();

	public ScreenScalesConfig()
	{
		this(DEFAULT_SCREEN_SCALES.clone());
	}

	public ScreenScalesConfig(final double... initialScales)
	{
		this.screenScales.set(new ScreenScales(initialScales.clone()));
	}

	public ObjectProperty<ScreenScales> screenScalesProperty()
	{
		return this.screenScales;
	}

	public void set(final ScreenScalesConfig that)
	{
		this.screenScales.set(that.screenScales.get());
	}

	public static double[] defaultScreenScalesCopy()
	{
		return DEFAULT_SCREEN_SCALES.clone();
	}

	@Override
	public String toString()
	{
		return String.format(
				"{ScreenScalesConfig: %s}",
				this.screenScales.get() == null ? null : this.screenScales.get().toString()
		);
	}

}
