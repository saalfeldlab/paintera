package org.janelia.saalfeldlab.paintera.meshes;

import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ObservableMeshProgress extends ObservableWithListenersList {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected final AtomicInteger numTasks = new AtomicInteger();
  protected final AtomicInteger numCompletedTasks = new AtomicInteger();

  public int getNumTasks() {

	return this.numTasks.get();
  }

  public int getNumCompletedTasks() {

	return this.numCompletedTasks.get();
  }
}
