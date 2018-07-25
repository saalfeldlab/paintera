package org.janelia.saalfeldlab.paintera.state;

import bdv.util.volatiles.VolatileTypeMatcher;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;

public class RawSourceState<D, T extends RealType<T>>
		extends MinimalSourceState<D, T, DataSource<D, T>, ARGBColorConverter<T>>
{

	public RawSourceState(
			final DataSource<D, T> dataSource,
			final ARGBColorConverter<T> converter,
			final Composite<ARGBType, ARGBType> composite,
			final String name)
	{
		super(dataSource, converter, composite, name);
	}

	public static <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>>
	RawSourceState<D, T> simpleSourceFromSingleRAI(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final double min,
			final double max,
			final String name)
	{

		if (!Views.isZeroMin(data))
		{
			return simpleSourceFromSingleRAI(Views.zeroMin(data), resolution, offset, min, max, name);
		}

		final AffineTransform3D mipmapTransform = new AffineTransform3D();
		mipmapTransform.set(
				resolution[0], 0, 0, offset[0],
				0, resolution[1], 0, offset[1],
				0, 0, resolution[2], offset[2]
		                   );

		@SuppressWarnings("unchecked") final T vt = (T) VolatileTypeMatcher.getVolatileTypeForType(Util
				.getTypeFromInterval(
				data)).createVariable();
		vt.setValid(true);
		final RandomAccessibleInterval<T> vdata = Converters.convert(data, (s, t) -> t.get().set(s), vt);

		final RandomAccessibleIntervalDataSource<D, T> dataSource = new RandomAccessibleIntervalDataSource<>(
				data,
				vdata,
				mipmapTransform,
				i -> new NearestNeighborInterpolatorFactory<>(),
				i -> new NearestNeighborInterpolatorFactory<>(),
				name
		);

		return new RawSourceState<>(
				dataSource,
				new ARGBColorConverter.InvertingImp0<>(min, max),
				new CompositeCopy<>(),
				name
		);

	}

}
