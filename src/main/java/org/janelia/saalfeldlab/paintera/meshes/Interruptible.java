package org.janelia.saalfeldlab.paintera.meshes;

public interface Interruptible<T>
{

	public void interruptFor(T t);

}
