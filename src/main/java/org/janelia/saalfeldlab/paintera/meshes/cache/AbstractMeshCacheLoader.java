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
		implements CacheLoader<ShapeKey<K>, Pair<float[], float[]>>, Interruptible<ShapeKey<K>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected final int[] cubeSize;

	protected final Supplier<RandomAccessibleInterval<T>> data;

	protected final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator;

	protected final AffineTransform3D transform;

	protected final List<Consumer<ShapeKey<K>>> interruptListeners = new ArrayList<>();

	public AbstractMeshCacheLoader(
			final int[] cubeSize,
			final Supplier<RandomAccessibleInterval<T>> data,
			final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)
	{
		super();
		LOG.debug("Constructiong {}", getClass().getName());
		this.cubeSize = cubeSize;
		this.data = data;
		this.getMaskGenerator = getMaskGenerator;
		this.transform = transform;
	}

	@Override
	public void interruptFor(final ShapeKey<K> key)
	{
		synchronized (interruptListeners)
		{
			interruptListeners.forEach(l -> l.accept(key));
		}
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

		final AtomicBoolean isInterrupted = new AtomicBoolean();
		final Consumer<ShapeKey<K>> listener = interruptedKey -> {
			if (interruptedKey.equals(key))
				isInterrupted.set(true);
		};
		synchronized (interruptListeners)
		{
			interruptListeners.add(listener);
		}

		try
		{
			final float[] mesh = new MarchingCubes<>(
					Views.extendZero(mask),
					key.interval(),
					transform,
					cubeSize,
					() -> isInterrupted.get() || Thread.currentThread().isInterrupted()
			).generateMesh();

			if (isInterrupted.get())
			{
				LOG.debug("Mesh generation was interrupted for key {}", key);
				Thread.currentThread().interrupt();
				throw new InterruptedException();
			}

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
		finally
		{
			synchronized (interruptListeners)
			{
				interruptListeners.remove(listener);
			}
		}
	}
}
