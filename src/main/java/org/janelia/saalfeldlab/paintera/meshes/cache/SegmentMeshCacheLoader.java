package org.janelia.saalfeldlab.paintera.meshes.cache;

import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class SegmentMeshCacheLoader<T> extends AbstractMeshCacheLoader<T, TLongHashSet>
{
	public SegmentMeshCacheLoader(
			final Supplier<RandomAccessibleInterval<T>> data,
			final BiFunction<TLongHashSet, Double, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super(data, getMaskGenerator, transform);
	}
}
