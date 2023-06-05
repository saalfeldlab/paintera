package org.janelia.saalfeldlab.paintera.meshes;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetBlockListFor;
import org.janelia.saalfeldlab.paintera.meshes.managed.GetMeshFor;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MeshExporter<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	protected int numberOfFaces = 0;

	public void exportMesh(
			final GetBlockListFor<T> getBlockListFor,
			final GetMeshFor<T> getMeshFor,
			final T[] ids,
			final int scale,
			final String[] paths) {

		assert ids.length == paths.length;
		for (int i = 0; i < ids.length; i++) {
			numberOfFaces = 0;
			exportMesh(getBlockListFor, getMeshFor, ids[i], scale, paths[i]);
		}
	}

	public void exportMesh(
			final GetBlockListFor<T> getBlockListFor,
			final GetMeshFor<T> getMeshFor,
			final T id,
			final int scaleIndex,
			final String path) {
		// all blocks from id
		final Set<HashWrapper<Interval>> blockSet = new HashSet<>();

		final Interval[] blocksOrNull = getBlockListFor.getBlocksFor(scaleIndex, id);
		Arrays
				.stream(blocksOrNull == null ? new Interval[0] : blocksOrNull)
				.map(HashWrapper::interval)
				.forEach(blockSet::add);

		final Interval[] blocks = blockSet.stream().map(HashWrapper::getData).toArray(Interval[]::new);

		// generate keys from blocks, scaleIndex, and id
		final List<ShapeKey<T>> keys = new ArrayList<>();
		for (final Interval block : blocks) {
			// ignoring simplification iterations parameter
			// TODO consider smoothing parameters
			keys.add(new ShapeKey<>(
					id,
					scaleIndex,
					0,
					0,
					0,
					0,
					Intervals.minAsLongArray(block),
					Intervals.maxAsLongArray(block)
			));
		}

		for (final ShapeKey<T> key : keys) {
			PainteraTriangleMesh verticesAndNormals;
			try {
				verticesAndNormals = getMeshFor.getMeshFor(key);
				if (verticesAndNormals == null)
					continue;
				assert verticesAndNormals.getVertices().length == verticesAndNormals.getNormals().length : "Vertices and normals must have the same size.";
				try {
					save(
							path,
							id.toString(),
							verticesAndNormals.getVertices(),
							verticesAndNormals.getNormals(),
							hasFaces(numberOfFaces));

					numberOfFaces += verticesAndNormals.getVertices().length / 3;
				} catch (final IOException e) {
					Exceptions.exceptionAlert("Mesh exporter", "Couldn't write file", e).show();
					break;
				}
			} catch (final RuntimeException e) {
				LOG.warn("{} : {}", e.getClass(), e.getMessage());
				e.printStackTrace();
				throw e;
			}
		}

	}

	protected abstract void save(String path, String id, float[] vertices, float[] normals, boolean append) throws IOException;

	public static boolean hasFaces(final int numberOfFaces) {

		return numberOfFaces > 0;
	}

}
