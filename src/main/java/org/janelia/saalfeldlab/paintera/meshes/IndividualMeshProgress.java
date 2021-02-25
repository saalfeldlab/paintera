package org.janelia.saalfeldlab.paintera.meshes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class IndividualMeshProgress extends ObservableMeshProgress {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void set(final int numTasks, final int numCompletedTasks) {

	this.numTasks.set(numTasks);
	this.numCompletedTasks.set(numCompletedTasks);
	stateChanged();
  }

  public void setNumTasks(final int numTasks) {

	this.numTasks.set(numTasks);
	stateChanged();
  }

  public void setNumCompletedTasks(final int numCompletedTasks) {

	this.numCompletedTasks.set(numCompletedTasks);
	stateChanged();
  }

  public void incrementNumCompletedTasks() {

	this.numCompletedTasks.incrementAndGet();
	stateChanged();
  }
}
