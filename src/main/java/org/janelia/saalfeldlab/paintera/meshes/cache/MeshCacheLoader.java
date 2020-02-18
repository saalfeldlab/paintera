package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;

import java.util.function.BiFunction;

public class MeshCacheLoader<T> extends AbstractMeshCacheLoader<T, Long>
{
	public MeshCacheLoader(
			final int[] cubeSize,
			final RandomAccessibleInterval<T> data,
			final BiFunction<Long, Double, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super(
				cubeSize,
				() -> data,
				getMaskGenerator,
				transform);
	}
}
