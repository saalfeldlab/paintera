package org.janelia.saalfeldlab.paintera.state;

import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.paint.Color;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.PredicateDataSource;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState.Threshold;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState.VolatileMaskConverter;
import org.janelia.saalfeldlab.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThresholdingSourceState<D extends RealType<D>, T extends AbstractVolatileRealType<D, T>>
		extends
		MinimalSourceState<BoolType, Volatile<BoolType>, PredicateDataSource<D, T, Threshold<D>>,
				VolatileMaskConverter<BoolType, Volatile<BoolType>>>
{

	private final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.WHITE);

	private final DoubleProperty alpha = new SimpleDoubleProperty(1.0);

	private final Threshold<D> threshold;

	public ThresholdingSourceState(
			final String name,
			final RawSourceState<D, T> toBeThresholded)
	{
		super(
				threshold(
						toBeThresholded.dataSource(),
						toBeThresholded.converter().minProperty(),
						toBeThresholded.converter().maxProperty(),
						name
				         ),
				new VolatileMaskConverter<>(),
				new ARGBCompositeAlphaAdd(),
				name,
				toBeThresholded
		     );
		this.threshold = getDataSource().getPredicate();
		this.axisOrderProperty().bindBidirectional(toBeThresholded.axisOrderProperty());
	}

	public Threshold<D> getThreshold()
	{
		return this.threshold;
	}

	private static <D extends RealType<D>, T extends AbstractVolatileRealType<D, T>> PredicateDataSource<D, T,
			Threshold<D>> threshold(
			final DataSource<D, T> source,
			final ObservableDoubleValue min,
			final ObservableDoubleValue max,
			final String name)
	{
		return new PredicateDataSource<>(source, new Threshold<>(min, max), name);
	}

	public ObjectProperty<Color> colorProperty()
	{
		return this.color;
	}

	public DoubleProperty alphaProperty()
	{
		return this.alpha;
	}

	public static class MaskConverter<B extends BooleanType<B>> implements Converter<B, ARGBType>
	{

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final B mask, final ARGBType color)
		{
			color.set(mask.get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked)
		{
			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked)
		{
			this.notMasked.set(masked);
		}

	}

	public static class VolatileMaskConverter<B extends BooleanType<B>, V extends Volatile<B>>
			implements Converter<V, ARGBType>
	{

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private final ARGBType masked = Colors.toARGBType(Color.rgb(255, 255, 255, 1.0));

		private final ARGBType notMasked = Colors.toARGBType(Color.rgb(0, 0, 0, 0.0));

		@Override
		public void convert(final V mask, final ARGBType color)
		{
			color.set(mask.get().get() ? masked : notMasked);
		}

		public void setMasked(final ARGBType masked)
		{
			this.masked.set(masked);
		}

		public void setNotMasked(final ARGBType notMasked)
		{
			this.notMasked.set(notMasked);
		}

		public ARGBType getMasked()
		{
			return masked;
		}

		public ARGBType getNotMasked()
		{
			return notMasked;
		}
	}

	public static class Threshold<T extends RealType<T>> implements Predicate<T>
	{

		private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

		private double min;

		private double max;

		private final ObservableDoubleValue minSupplier;

		private final ObservableDoubleValue maxSupplier;

		public Threshold(
				final ObservableDoubleValue minSupplier,
				final ObservableDoubleValue maxSupplier)
		{
			super();
			this.minSupplier = minSupplier;
			this.maxSupplier = maxSupplier;
			this.minSupplier.addListener((obs, oldv, newv) -> update());
			this.maxSupplier.addListener((obs, oldv, newv) -> update());
			update();
		}

		@Override
		public boolean test(final T t)
		{
			final double  val            = t.getRealDouble();
			final boolean isWithinMinMax = val < this.max && val > this.min;
			return isWithinMinMax;
		}

		private void update()
		{
			final double m = this.minSupplier.get();
			final double M = this.maxSupplier.get();

			if (m < M)
			{
				this.min = m;
				this.max = M;
			}
			else
			{
				this.min = M;
				this.max = m;
			}
		}

		public ObservableDoubleValue minValue()
		{
			return this.minSupplier;
		}

		public ObservableDoubleValue maxValue()
		{
			return this.maxSupplier;
		}


	}

}
