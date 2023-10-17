package org.janelia.saalfeldlab.paintera.meshes;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
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
import java.util.*;

public abstract class MeshExporter<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public void exportMesh(
			final GetBlockListFor<T> getBlockListFor,
			final GetMeshFor<T> getMeshFor,
			final MeshSettings[] meshSettings,
			final T[] ids,
			final int scale,
			final String path) {

		for (int i = 0; i < ids.length; i++) {
			exportMesh(getBlockListFor, getMeshFor, meshSettings[i], ids[i], scale, path, i != 0);
		}
	}

	public void exportMesh(
			final GetBlockListFor<T> getBlockListFor,
			final GetMeshFor<T> getMeshFor,
			final MeshSettings meshSettings,
			final T id,
			final int scaleIndex,
			final String path,
			final boolean append) {
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
					meshSettings.getSimplificationIterations(),
					meshSettings.getSmoothingLambda(),
					meshSettings.getSmoothingIterations(),
					meshSettings.getMinLabelRatio(),
					Intervals.minAsLongArray(block),
					Intervals.maxAsLongArray(block)
			));
		}

		final var vertices = new TFloatArrayList();
		final var normals = new TFloatArrayList();
		final var indices = new TIntArrayList();
		for (final ShapeKey<T> key : keys) {
			PainteraTriangleMesh verticesAndNormals;
			verticesAndNormals = getMeshFor.getMeshFor(key);
			if (verticesAndNormals == null) {
				continue;
			}
			assert verticesAndNormals.getVertices().length == verticesAndNormals.getNormals().length : "Vertices and normals must have the same size.";
			var indexOffset = vertices.size() / 3;
			vertices.add(verticesAndNormals.getVertices());
			normals.add(verticesAndNormals.getNormals());
			for (int index : verticesAndNormals.getIndices()) {
				indices.add(indexOffset + index + 1);
			}
		}

		try {
			save(path, id.toString(), vertices.toArray(), normals.toArray(), indices.toArray(), append);
		} catch (final IOException e) {
			Exceptions.exceptionAlert("Mesh exporter", "Couldn't write file", e).show();
		}
	}

	protected abstract void save(String path, String id, float[] vertices, float[] normals, int[] indices, boolean append) throws IOException;

}
