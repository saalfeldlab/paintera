package org.janelia.saalfeldlab.paintera.data;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.ByteType;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
public class DataSourceTest {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class DummyDataSource implements DataSource<ByteType, ByteType> {

		private final AffineTransform3D[] transforms;

		private DummyDataSource(AffineTransform3D[] transforms) {

			this.transforms = transforms;
		}

		@Override
		public RandomAccessibleInterval<ByteType> getDataSource(int t, int level) {

			return null;
		}

		@Override
		public RealRandomAccessible<ByteType> getInterpolatedDataSource(int t, int level, Interpolation method) {

			return null;
		}

		@Override
		public ByteType getDataType() {

			return null;
		}

		@Override
		public boolean isPresent(int i) {

			return false;
		}

		@Override
		public RandomAccessibleInterval<ByteType> getSource(int i, int i1) {

			return null;
		}

		@Override
		public RealRandomAccessible<ByteType> getInterpolatedSource(int i, int i1, Interpolation interpolation) {

			return null;
		}

		@Override
		public void getSourceTransform(int t, int level, AffineTransform3D tf) {

			tf.set(transforms[level]);
		}

		@Override
		public ByteType getType() {

			return null;
		}

		@Override
		public String getName() {

			return null;
		}

		@Override
		public VoxelDimensions getVoxelDimensions() {

			return null;
		}

		@Override
		public int getNumMipmapLevels() {

			return transforms.length;
		}

		@Override
		public void invalidate(Long key) {

		}

		@Override
		public void invalidateIf(long parallelismThreshold, Predicate<Long> condition) {

		}

		@Override
		public void invalidateAll(long parallelismThreshold) {

		}

		@Override
		public void invalidateIf(Predicate<Long> condition) {

		}

		@Override
		public void invalidateAll() {

		}
	}

	@Test
	public void testGetScale() {

		double[][] resolutions = {
				{1.0, 1.0, 1.0},
				{2.0, 3.0, 4.0},
				{6.0, 12.0, 20.0}
		};

		double[] zeroOffset = new double[]{0.0, 0.0, 0.0};
		double[] ratio = new double[3];

		AffineTransform3D[] transforms = Arrays
				.stream(resolutions)
				.map(r -> N5Helpers.fromResolutionAndOffset(r, zeroOffset))
				.toArray(AffineTransform3D[]::new);

		DummyDataSource source = new DummyDataSource(transforms);
		assertEquals(resolutions.length, source.getNumMipmapLevels());
		for (int level = 0; level < source.getNumMipmapLevels(); ++level) {
			assertArrayEquals(resolutions[level], DataSource.getScale(source, 0, level), 0.0);
			for (int targetLevel = 0; targetLevel < source.getNumMipmapLevels(); ++targetLevel) {
				ratio[0] = resolutions[targetLevel][0] / resolutions[level][0];
				ratio[1] = resolutions[targetLevel][1] / resolutions[level][1];
				ratio[2] = resolutions[targetLevel][2] / resolutions[level][2];
				LOG.debug("Ratio for {} {} is {}", level, targetLevel, ratio);
				assertArrayEquals(ratio, DataSource.getRelativeScales(source, 0, level, targetLevel), 0.0);
			}
		}

	}

}
