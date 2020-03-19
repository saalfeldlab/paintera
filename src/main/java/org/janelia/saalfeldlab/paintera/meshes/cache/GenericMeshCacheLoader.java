package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.AverageNormals;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Normals;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
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
		implements CacheLoader<ShapeKey<K>, PainteraTriangleMesh>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<RandomAccessibleInterval<B>> data;

	private final IntFunction<AffineTransform3D> transform;

	public GenericMeshCacheLoader(
			final IntFunction<RandomAccessibleInterval<B>> data,
			final IntFunction<AffineTransform3D> transform)
	{
		super();
		LOG.debug("Constructing {}", getClass().getName());
		this.data = data;
		this.transform = transform;
		//		this.containedLabelsInBlock = containedLabelsInBlock;
	}

	@Override
	public PainteraTriangleMesh get(final ShapeKey<K> key) throws Exception
	{

		LOG.debug("key={}", key);
		final RandomAccessibleInterval<B> mask = data.apply(key.scaleIndex());
		final AffineTransform3D transform = this.transform.apply(key.scaleIndex());

		final float[] mesh = new MarchingCubes<>(
				Views.extendZero(mask),
				key.interval(),
				transform).generateMesh();
		final float[] normals = new float[mesh.length];
		if (key.smoothingIterations() > 0) {
			final float[] smoothMesh = Smooth.smooth(mesh, key.smoothingLambda(), key.smoothingIterations());
			System.arraycopy(smoothMesh, 0, mesh, 0, mesh.length);
		}
		Normals.normals(mesh, normals);
		AverageNormals.averagedNormals(mesh, normals);

		// TODO should this even happen? Probably not!
		for (int i = 0; i < normals.length; ++i) {
			normals[i] *= -1;
		}
		return new PainteraTriangleMesh(mesh, normals);
	}
}
