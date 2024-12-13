package org.janelia.saalfeldlab.paintera.state;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ConstantUtils;
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBColorConverter;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.RandomAccessibleIntervalDataSource;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SourceInfoTest {

	private static Invalidate<Long> NO_OP_INVALIDATE = new Invalidate<Long>() {

		@Override
		public void invalidate(Long key) {

		}

		@Override
		public void invalidateIf(long parallelismThreshold, Predicate<Long> condition) {

		}

		@Override
		public void invalidateAll(long parallelismThreshold) {

		}
	};

	private final FinalInterval interval = new FinalInterval(100, 200, 300);

	private final RandomAccessibleInterval<DoubleType> rai = ConstantUtils
			.constantRandomAccessibleInterval(new DoubleType(1.0), interval);

	private final RandomAccessibleIntervalDataSource<DoubleType, DoubleType> source1 = new
			RandomAccessibleIntervalDataSource<>(
			rai,
			rai,
			() -> new AffineTransform3D(),
			NO_OP_INVALIDATE,
			i -> new NearestNeighborInterpolatorFactory<>(),
			i -> new NearestNeighborInterpolatorFactory<>(),
			"source1"
	);

	private final RandomAccessibleIntervalDataSource<DoubleType, DoubleType> source2 = new
			RandomAccessibleIntervalDataSource<>(
			rai,
			rai,
			() -> new AffineTransform3D(),
			NO_OP_INVALIDATE,
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

	@Test
	public void testThrowsHasDependents() {

		assertThrows(HasDependents.class, () -> {
			final SourceInfo si = new SourceInfo();
			si.addState(state1);
			si.addState(state2);
			assertEquals(2, si.numSources().get());
			si.removeSource(state1.getDataSource());
		});
	}

	@Test()
	public void testForceRemoveDependents() throws HasDependents {

		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		assertEquals(2, si.numSources().get());
		si.removeSource(state1.getDataSource(), true);
		assertEquals(1, si.numSources().get());
		si.removeSource(state2.getDataSource());
		assertEquals(0, si.numSources().get());
	}

	@Test()
	public void testRemove() throws HasDependents {

		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		assertEquals(2, si.numSources().get());
		si.removeSource(state2.getDataSource(), true);
		assertEquals(1, si.numSources().get());
		si.removeSource(state1.getDataSource());
		assertEquals(0, si.numSources().get());
	}

	@Test()
	public void testRemoveAll() throws HasDependents {

		final SourceInfo si = new SourceInfo();
		si.addState(state1);
		si.addState(state2);
		assertEquals(2, si.numSources().get());
		si.removeAllSources();
		assertEquals(0, si.numSources().get());
	}

}
