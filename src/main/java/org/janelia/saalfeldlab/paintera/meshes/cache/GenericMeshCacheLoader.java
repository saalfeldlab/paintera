package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.AverageNormals;
import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Normals;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.Smooth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public class GenericMeshCacheLoader<K, B extends BooleanType<B>>
		implements CacheLoader<ShapeKey<K>, Pair<float[], float[]>>, Interruptible<ShapeKey<K>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final int[] cubeSize;

	private final IntFunction<RandomAccessibleInterval<B>> data;

	private final IntFunction<AffineTransform3D> transform;

	private final List<Consumer<ShapeKey<K>>> interruptListeners = new ArrayList<>();

	//	private final InterruptibleFunction< HashWrapper< long[] >, long[] > containedLabelsInBlock;

	public GenericMeshCacheLoader(
			final int[] cubeSize,
			final IntFunction<RandomAccessibleInterval<B>> data,
			final IntFunction<AffineTransform3D> transform)//,
	//			final InterruptibleFunction< HashWrapper< long[] >, long[] > containedLabelsInBlock )
	{
		super();
		LOG.debug("Constructiong {}", getClass().getName());
		this.cubeSize = cubeSize;
		this.data = data;
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
	public Pair<float[], float[]> get(final ShapeKey<K> key) throws Exception
	{

		//		if ( key.meshSimplificationIterations() > 0 )
		//		{
		// TODO deal with mesh simplification
		//		}

		LOG.debug("key={}", key);
		final RandomAccessibleInterval<B> mask = data.apply(key.scaleIndex());
		final AffineTransform3D transform = this.transform.apply(key.scaleIndex());

		final boolean[] isInterrupted = new boolean[] {false};
		final Consumer<ShapeKey<K>> listener = interruptedKey -> {
			if (interruptedKey.equals(key))
			{
				isInterrupted[0] = true;
			}
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
					() -> isInterrupted[0]).generateMesh();
			final float[] normals = new float[mesh.length];
			if (key.smoothingIterations() > 0)
			{
				final float[] smoothMesh = Smooth.smooth(mesh, key.smoothingLambda(), key.smoothingIterations());
				System.arraycopy(smoothMesh, 0, mesh, 0, mesh.length);
			}
			Normals.normals(mesh, normals);
			AverageNormals.averagedNormals(mesh, normals);

			for (int i = 0; i < normals.length; ++i)
			{
				normals[i] *= -1;
			}
			return isInterrupted[0] ? null : new ValuePair<>(mesh, normals);
		} finally
		{
			synchronized (interruptListeners)
			{
				interruptListeners.remove(listener);
			}
		}
	}
}