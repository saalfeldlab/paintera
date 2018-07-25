package org.janelia.saalfeldlab.paintera.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MeshExporter<T>
{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected int numberOfFaces = 0;

	public void exportMesh(
			final Function<T, Interval[]>[][] blockListCaches,
			final Function<ShapeKey<T>, Pair<float[], float[]>>[][] meshCaches,
			final T[] ids,
			final int scale,
			final String[] paths)
	{
		assert ids.length == paths.length;
		for (int i = 0; i < ids.length; i++)
		{
			numberOfFaces = 0;
			exportMesh(blockListCaches[i], meshCaches[i], ids[i], scale, paths[i]);
		}
	}

	public void exportMesh(
			final Function<T, Interval[]>[] blockListCache,
			final Function<ShapeKey<T>, Pair<float[], float[]>>[] meshCache,
			final T id,
			final int scaleIndex,
			final String path)
	{
		// all blocks from id
		final Set<HashWrapper<Interval>> blockSet = new HashSet<>();

		Arrays
				.stream(blockListCache[scaleIndex].apply(id))
				.map(HashWrapper::interval)
				.forEach(blockSet::add);

		final Interval[] blocks = blockSet.stream().map(HashWrapper::getData).toArray(Interval[]::new);

		// generate keys from blocks, scaleIndex, and id
		final List<ShapeKey<T>> keys = new ArrayList<>();
		for (final Interval block : blocks)
		{
			// ignoring simplification iterations parameter
			// TODO consider smoothing parameters
			keys.add(new ShapeKey<>(
					id,
					scaleIndex,
					0,
					0,
					0,
					Intervals.minAsLongArray(block),
					Intervals.maxAsLongArray(block)
			));
		}

		for (final ShapeKey<T> key : keys)
		{
			Pair<float[], float[]> verticesAndNormals;
			try
			{
				verticesAndNormals = meshCache[scaleIndex].apply(key);
				assert verticesAndNormals.getA().length == verticesAndNormals.getB().length : "Vertices and normals " +
						"must have the same size.";
				save(
						path,
						id.toString(),
						verticesAndNormals.getA(),
						verticesAndNormals.getB(),
						hasFaces(numberOfFaces)
				    );
				numberOfFaces += verticesAndNormals.getA().length / 3;
			} catch (final RuntimeException e)
			{
				LOG.warn("{} : {}", e.getClass(), e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}

	}

	protected abstract void save(String path, String id, float[] vertices, float[] normals, boolean append);

	public static boolean hasFaces(final int numberOfFaces)
	{
		return numberOfFaces > 0;
	}

}
