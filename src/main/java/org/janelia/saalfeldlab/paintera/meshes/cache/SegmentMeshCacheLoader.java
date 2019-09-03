package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.util.function.Function;
import java.util.function.Supplier;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;

public class SegmentMeshCacheLoader<T> extends AbstractMeshCacheLoader<T, TLongHashSet>
{
	public SegmentMeshCacheLoader(
			final int[] cubeSize,
			final Supplier<RandomAccessibleInterval<T>> data,
			final Function<TLongHashSet, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super(
				cubeSize,
				data,
				getMaskGenerator,
				transform
			);
	}
}
