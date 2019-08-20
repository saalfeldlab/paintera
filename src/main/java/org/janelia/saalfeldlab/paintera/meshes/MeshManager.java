package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Map;

import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

public interface MeshManager<N, T> extends Observable
{
	void generateMesh(final N id);

	void removeMesh(final N id);

	void removeAllMeshes();

	IntegerProperty preferredScaleLevelProperty();

	IntegerProperty highestScaleLevelProperty();

	IntegerProperty meshSimplificationIterationsProperty();

	DoubleProperty smoothingLambdaProperty();

	IntegerProperty smoothingIterationsProperty();

	Map<N, MeshGenerator<T>> unmodifiableMeshMap();

	InterruptibleFunction<T, Interval[]>[] blockListCache();

	InterruptibleFunction<ShapeKey<T>, Pair<float[], float[]>>[] meshCache();

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

	default void invalidateMeshCaches() {}

}
