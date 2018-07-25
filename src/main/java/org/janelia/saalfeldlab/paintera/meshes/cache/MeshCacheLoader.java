package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Intervals;
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

public class MeshCacheLoader<T>
		implements CacheLoader<ShapeKey<Long>, Pair<float[], float[]>>, Interruptible<ShapeKey<Long>>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final int[] cubeSize;

	private final RandomAccessibleInterval<T> data;

	private final LongFunction<Converter<T, BoolType>> getMaskGenerator;

	private final AffineTransform3D transform;

	private final List<Consumer<ShapeKey<Long>>> interruptListeners = new ArrayList<>();

	public MeshCacheLoader(
			final int[] cubeSize,
			final RandomAccessibleInterval<T> data,
			final LongFunction<Converter<T, BoolType>> getMaskGenerator,
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
	public void interruptFor(final ShapeKey<Long> key)
	{
		synchronized (interruptListeners)
		{
			interruptListeners.forEach(l -> l.accept(key));
		}
	}

	@Override
	public Pair<float[], float[]> get(final ShapeKey<Long> key) throws Exception
	{

		//		if ( key.meshSimplificationIterations() > 0 )
		//		{
		// TODO deal with mesh simplification
		//		}

		LOG.debug("key={}, getMaskGenerator={}", key, getMaskGenerator);
		final RandomAccessibleInterval<BoolType> mask = Converters.convert(
				data,
				getMaskGenerator.apply(key.shapeId()),
				new BoolType(false)
		                                                                  );

		final boolean[] isInterrupted = new boolean[] {false};
		final Consumer<ShapeKey<Long>> listener = interruptedKey -> {
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
					Intervals.expand(key.interval(), Arrays.stream(cubeSize).mapToLong(size -> size).toArray()),
					transform,
					cubeSize,
					() -> isInterrupted[0]
			).generateMesh();
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
			synchronized (interruptListeners)
			{
				return isInterrupted[0] ? new ValuePair<>(mesh, normals) : null;
			}
		} finally
		{
			synchronized (interruptListeners)
			{
				interruptListeners.remove(listener);
			}
		}
	}
}
