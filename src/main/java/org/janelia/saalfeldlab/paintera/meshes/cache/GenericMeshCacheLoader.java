package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.BooleanType;
import net.imglib2.util.Triple;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Mesh;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.IntFunction;

public class GenericMeshCacheLoader<K, B extends BooleanType<B>>
		implements CacheLoader<ShapeKey<K>, PainteraTriangleMesh> {

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
	//		this.containedLabelsInBlock = containedLabelsInBlock;
  }

  @Override
  public PainteraTriangleMesh get(final ShapeKey<K> key) throws Exception {

	LOG.debug("key={}", key);
	final RandomAccessibleInterval<B> mask = data.apply(key.scaleIndex());
	final AffineTransform3D transform = this.transform.apply(key.scaleIndex());

	final float[] vertices = new MarchingCubes<>(
			Views.extendZero(mask),
			key.interval()
	).generateMesh();

	final Mesh meshMesh = new Mesh(vertices, key.interval(), transform);
	if (key.smoothingIterations() > 0) {
	  meshMesh.smooth(key.smoothingLambda(), key.smoothingIterations());
	}
	meshMesh.averageNormals();

	final Triple<float[], float[], int[]> triple = meshMesh.export();
	final float[] mesh = triple.getA();

	final float[] normals = triple.getB();

	// TODO should this even happen? Probably not!
	for (int i = 0; i < normals.length; ++i) {
	  normals[i] *= -1;
	}

	return new PainteraTriangleMesh(mesh, normals);
  }
}
