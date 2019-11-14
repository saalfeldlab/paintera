package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import net.imglib2.Interval;
import net.imglib2.util.Pair;
import net.imglib2.util.Triple;

import java.util.Map;

public interface MeshManager<N, T>
{
	void addMesh(final N id);

	void removeMesh(final N id);

	void removeAllMeshes();

	void update();

	IntegerProperty levelOfDetailProperty();

	IntegerProperty coarsestScaleLevelProperty();

	IntegerProperty finestScaleLevelProperty();

	IntegerProperty meshSimplificationIterationsProperty();

	DoubleProperty smoothingLambdaProperty();

	IntegerProperty smoothingIterationsProperty();

	DoubleProperty minLabelRatioProperty();

	Map<N, MeshGenerator<T>> unmodifiableMeshMap();

	InterruptibleFunction<T, Interval[]>[] blockListCache();

	InterruptibleFunction<ShapeKey<T>, Triple<float[], float[], int[]>>[] meshCache();

	DoubleProperty opacityProperty();

	long[] containedFragments(N t);

	void refreshMeshes();

	BooleanProperty areMeshesEnabledProperty();

	BooleanProperty showBlockBoundariesProperty();

	IntegerProperty rendererBlockSizeProperty();

	IntegerProperty numElementsPerFrameProperty();

	LongProperty frameDelayMsecProperty();

	LongProperty sceneUpdateDelayMsecProperty();

	ManagedMeshSettings managedMeshSettings();

	default void invalidateCaches() {}

}
