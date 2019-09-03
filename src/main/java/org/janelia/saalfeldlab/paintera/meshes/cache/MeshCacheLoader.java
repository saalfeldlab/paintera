package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.util.function.LongFunction;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;

public class MeshCacheLoader<T> extends AbstractMeshCacheLoader<T, Long>
{
	public MeshCacheLoader(
			final int[] cubeSize,
			final RandomAccessibleInterval<T> data,
			final LongFunction<Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super(
				cubeSize,
				() -> data,
				k -> getMaskGenerator.apply(k.longValue()),
				transform
			);
	}
}
