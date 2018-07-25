package org.janelia.saalfeldlab.fx.event;

public interface InstallAndRemove<T>
{

	public void installInto(T t);

	public void removeFrom(T t);

}
