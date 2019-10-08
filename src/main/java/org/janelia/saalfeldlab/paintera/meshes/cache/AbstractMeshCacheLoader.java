package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.imglib2.util.*;
import org.janelia.saalfeldlab.paintera.meshes.AverageNormals;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Normals;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.Smooth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.view.Views;

public abstract class AbstractMeshCacheLoader<T, K>
		implements CacheLoader<ShapeKey<K>, Triple<float[], float[], int[]>>, Interruptible<ShapeKey<K>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected final int[] cubeSize;

	protected final Supplier<RandomAccessibleInterval<T>> data;

	protected final Function<K, Converter<T, BoolType>> getMaskGenerator;

	protected final AffineTransform3D transform;

	protected final List<Consumer<ShapeKey<K>>> interruptListeners = new ArrayList<>();

	//	private final InterruptibleFunction< HashWrapper< long[] >, long[] > containedLabelsInBlock;

	public AbstractMeshCacheLoader(
			final int[] cubeSize,
			final Supplier<RandomAccessibleInterval<T>> data,
			final Function<K, Converter<T, BoolType>> getMaskGenerator,
			final AffineTransform3D transform)//,
	//			final InterruptibleFunction< HashWrapper< long[] >, long[] > containedLabelsInBlock )
	{
		super();
		LOG.debug("Constructiong {}", getClass().getName());
		this.cubeSize = cubeSize;
		this.data = data;
		this.getMaskGenerator = getMaskGenerator;
		this.transform = transform;
		//		this.containedLabelsInBlock = containedLabelsInBlock;
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
	public Triple<float[], float[], int[]> get(final ShapeKey<K> key) throws Exception
	{

		//		if ( key.meshSimplificationIterations() > 0 )
		//		{
		// TODO deal with mesh simplification
		//		}

		LOG.debug("key={}, getMaskGenerator={}", key, getMaskGenerator);
		final RandomAccessibleInterval<BoolType> mask = Converters.convert(
				data.get(),
				getMaskGenerator.apply(key.shapeId()),
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
			int smoothingIterations = key.smoothingIterations();

			final float[] mesh = new MarchingCubes<>(
					Views.extendZero(mask),
					Intervals.expand(key.interval(), smoothingIterations + 1),
					transform,
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

			return new ValueTriple<>(mesh, normals, new int[0]);
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
