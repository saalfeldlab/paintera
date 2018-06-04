package org.janelia.saalfeldlab.paintera.meshes;

import java.util.Map;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import net.imglib2.Interval;
import net.imglib2.util.Pair;

public interface MeshManager< T >
{
	public void generateMesh( final T id );

	public void removeMesh( final T id );

	public void removeAllMeshes();

	public IntegerProperty scaleLevelProperty();

	public IntegerProperty meshSimplificationIterationsProperty();

	public DoubleProperty smoothingLambdaProperty();

	public IntegerProperty smoothingIterationsProperty();

	public Map< T, MeshGenerator< T > > unmodifiableMeshMap();

	public InterruptibleFunction< T, Interval[] >[] blockListCache();

	public InterruptibleFunction< ShapeKey< T >, Pair< float[], float[] > >[] meshCache();

	public DoubleProperty opacityProperty();

	public long[] containedFragments( T t );

	public void refreshMeshes();

}
