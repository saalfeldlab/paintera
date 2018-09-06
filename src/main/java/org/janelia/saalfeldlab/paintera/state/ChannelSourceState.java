package org.janelia.saalfeldlab.paintera.state;

import net.imglib2.Volatile;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;

public class ChannelSourceState<
		D extends RealType<D>,
		T extends AbstractVolatileRealType<D, T>,
		CT extends RealComposite<T>,
		V extends Volatile<CT>>
		extends MinimalSourceState<RealComposite<D>,
		V,
		ChannelDataSource<RealComposite<D>, V>,
		ARGBCompositeColorConverter<T, CT, V>> {

	public ChannelSourceState(
			final ChannelDataSource<RealComposite<D>, V> dataSource,
			final ARGBCompositeColorConverter<T, CT, V> converter,
			final Composite<ARGBType, ARGBType> composite,
			final String name) {
		super(dataSource, converter, composite, name);
	}

	public long numChannels()
	{
		return getDataSource().numChannels();
	}
}
