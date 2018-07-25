package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Map;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

public interface MeshManager<N, T>
{
	public void generateMesh(final N id);

	public void removeMesh(final N id);

	public void removeAllMeshes();

	public IntegerProperty scaleLevelProperty();

	public IntegerProperty meshSimplificationIterationsProperty();

	public DoubleProperty smoothingLambdaProperty();

	public IntegerProperty smoothingIterationsProperty();

	public Map<N, MeshGenerator<T>> unmodifiableMeshMap();

	public InterruptibleFunction<T, Interval[]>[] blockListCache();

	public InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache();

	public DoubleProperty opacityProperty();

	public long[] containedFragments(N t);

	public void refreshMeshes();

	public BooleanProperty areMeshesEnabledProperty();

}
