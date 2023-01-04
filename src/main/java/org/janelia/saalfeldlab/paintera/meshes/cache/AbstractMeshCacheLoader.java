package org.janelia.saalfeldlab.paintera.meshes.cache;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.paintera.meshes.MarchingCubes;
import org.janelia.saalfeldlab.paintera.meshes.Mesh;
import org.janelia.saalfeldlab.paintera.meshes.PainteraTriangleMesh;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public abstract class AbstractMeshCacheLoader<T, K>
		implements CacheLoader<ShapeKey<K>, PainteraTriangleMesh> {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected final Supplier<RandomAccessibleInterval<T>> data;

  protected final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator;

  protected final AffineTransform3D transform;

  public AbstractMeshCacheLoader(
		  final Supplier<RandomAccessibleInterval<T>> data,
		  final BiFunction<K, Double, Converter<T, BoolType>> getMaskGenerator,
		  final AffineTransform3D transform) {

	super();
	LOG.debug("Constructing {}", getClass().getName());
	this.data = data;
	this.getMaskGenerator = getMaskGenerator;
	this.transform = transform;
  }

  @Override
  public PainteraTriangleMesh get(final ShapeKey<K> key) throws Exception {

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

	int smoothingIterations = key.smoothingIterations();

	final float[] vertices = new MarchingCubes<>(
			Views.extendZero(mask),
			Intervals.expand(key.interval(), smoothingIterations + 2)
	).generateMesh();

	Mesh meshMesh = new Mesh(vertices, key.interval(), transform);

	if (key.smoothingIterations() > 0)
	  meshMesh.smooth(key.smoothingLambda(), key.smoothingIterations());

	meshMesh.averageNormals();

	return meshMesh.asPainteraTriangleMesh();
  }
}
