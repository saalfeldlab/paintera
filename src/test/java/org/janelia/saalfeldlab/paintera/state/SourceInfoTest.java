package org.janelia.saalfeldlab.paintera.state;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ConstantUtils;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.junit.Assert;
import org.junit.Test;

public class SourceInfoTest
{

	private final FinalInterval interval = new FinalInterval(100, 200, 300);

	private final RandomAccessibleInterval<DoubleType> rai = ConstantUtils
			.constantRandomAccessibleInterval(new DoubleType(1.0), 3, interval);

	private final RandomAccessibleIntervalDataSource<DoubleType, DoubleType> source1 = new
			RandomAccessibleIntervalDataSource<>(
			rai,
			rai,
			new AffineTransform3D(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			"source1"
	);

	private final RandomAccessibleIntervalDataSource<DoubleType, DoubleType> source2 = new
			RandomAccessibleIntervalDataSource<>(
			rai,
			rai,
			new AffineTransform3D(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			"source2"
	);

	private final SourceState<DoubleType, DoubleType> state1 = new MinimalSourceState<>(
			source1,
			new ARGBColorConverter.InvertingImp0<>(0, 1),
			new CompositeCopy<>(),
			source1.getName()
	);

	private final SourceState<DoubleType, DoubleType> state2 = new MinimalSourceState<>(
			source2,
			new ARGBColorConverter.InvertingImp0<>(0, 1),
			new CompositeCopy<>(),
			source2.getName(),
			state1
	);

	@Test(expected = HasDependents.class)
	public void testThrowsHasDependents() throws HasDependents
	{
		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		Assert.assertEquals(2, si.numSources().get());
		si.removeSource(state1.getDataSource());
	}

	@Test()
	public void testForceRemoveDependents() throws HasDependents
	{
		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		Assert.assertEquals(2, si.numSources().get());
		si.removeSource(state1.getDataSource(), true);
		Assert.assertEquals(1, si.numSources().get());
		si.removeSource(state2.getDataSource());
		Assert.assertEquals(0, si.numSources().get());
	}

	@Test()
	public void testRemove() throws HasDependents
	{
		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		Assert.assertEquals(2, si.numSources().get());
		si.removeSource(state2.getDataSource(), true);
		Assert.assertEquals(1, si.numSources().get());
		si.removeSource(state1.getDataSource());
		Assert.assertEquals(0, si.numSources().get());
	}

	@Test()
	public void testRemoveAll() throws HasDependents
	{
		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		Assert.assertEquals(2, si.numSources().get());
		si.removeAllSources();
		Assert.assertEquals(0, si.numSources().get());
	}

}
