package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Mesh;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.IntFunction;

public class GenericMeshCacheLoader<K, B extends BooleanType<B>> implements CacheLoader<ShapeKey<K>, PainteraTriangleMesh> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final IntFunction<RandomAccessibleInterval<B>> data;

	private final IntFunction<AffineTransform3D> transform;

	public GenericMeshCacheLoader(
			final IntFunction<RandomAccessibleInterval<B>> data,
			final IntFunction<AffineTransform3D> transform) {

		super();
		LOG.debug("Constructing {}", getClass().getName());
		this.data = data;
		this.transform = transform;
	}

	@Override
	public PainteraTriangleMesh get(final ShapeKey<K> key) throws Exception {

		LOG.debug("key={}", key);
		final RandomAccessibleInterval<B> mask = data.apply(key.scaleIndex());
		final AffineTransform3D transform = this.transform.apply(key.scaleIndex());

		final int smoothingIterations = key.smoothingIterations();

		final float[] vertices = new MarchingCubes<>(
				Views.extendZero(mask),
				Intervals.expand(key.interval(), smoothingIterations + 2)
		).generateMesh();

		final Mesh meshMesh = new Mesh(vertices, key.interval(), transform, key.overlap());
		if (smoothingIterations > 0)
			meshMesh.smooth(key.smoothingLambda(), smoothingIterations);

		meshMesh.averageNormals();

		return meshMesh.asPainteraTriangleMesh();
	}
}
