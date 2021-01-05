package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractMeshCacheLoader<T, K>
		implements CacheLoader<ShapeKey<K>, Pair<float[], float[]>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected final Supplier<RandomAccessibleInterval<T>> data;

	protected final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator;

	protected final AffineTransform3D transform;

	public AbstractMeshCacheLoader(
			final Supplier<RandomAccessibleInterval<T>> data,
			final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super();
		LOG.debug("Constructiong {}", getClass().getName());
		this.data = data;
		this.getMaskGenerator = getMaskGenerator;
		this.transform = transform;
	}

	@Override
	public Pair<float[], float[]> get(final ShapeKey<K> key) throws Exception
	{

		//		if ( key.meshSimplificationIterations() > 0 )
		//		{
		// TODO deal with mesh simplification
		//		}

		LOG.debug("key={}, getMaskGenerator={}", key, getMaskGenerator);
		final RandomAccessibleInterval<BoolType> mask = Converters.convert(
				data.get(),
				getMaskGenerator.apply(key.shapeId(), key.minLabelRatio()),
				new BoolType(false)
			);

		final float[] mesh = new MarchingCubes<>(
				Views.extendZero(mask),
				key.interval(),
				transform).generateMesh();
		final float[] normals = new float[mesh.length];
		if (key.smoothingIterations() > 0)
		{
			final float[] smoothMesh = Smooth.smooth(mesh, key.smoothingLambda(), key.smoothingIterations());
			System.arraycopy(smoothMesh, 0, mesh, 0, mesh.length);
		}
		Normals.normals(mesh, normals);
		AverageNormals.averagedNormals(mesh, normals);

		for (int i = 0; i < normals.length; ++i)
			normals[i] *= -1;

		return new ValuePair<>(mesh, normals);
	}
}
