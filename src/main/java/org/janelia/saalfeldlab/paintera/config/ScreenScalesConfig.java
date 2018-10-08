package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;

public class ScreenScalesConfig {

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

	public ScreenScalesConfig(final double[] initialScales)
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

}
