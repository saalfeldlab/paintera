package org.janelia.saalfeldlab.paintera.meshes;

import javafx.beans.InvalidationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GlobalMeshProgress extends ObservableMeshProgress {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final class MeshProgressValues {

	int numTasks;
	int numCompletedTasks;

	MeshProgressValues(final int numTasks, final int numCompletedTasks) {

	  this.numTasks = numTasks;
	  this.numCompletedTasks = numCompletedTasks;
	}

	private void set(final MeshProgressValues other) {

	  this.numTasks = other.numTasks;
	  this.numCompletedTasks = other.numCompletedTasks;
	}
  }

  private final Map<ObservableMeshProgress, MeshProgressValues> meshProgresses = new HashMap<>();

  private final InvalidationListener updateListener = obs ->
  {
	assert obs instanceof ObservableMeshProgress;
	final ObservableMeshProgress meshProgress = (ObservableMeshProgress)obs;

	final MeshProgressValues values = this.meshProgresses.get(meshProgress);
	final MeshProgressValues newValues = new MeshProgressValues(meshProgress.getNumTasks(), meshProgress.getNumCompletedTasks());
	this.numTasks.addAndGet(newValues.numTasks - values.numTasks);
	this.numCompletedTasks.addAndGet(newValues.numCompletedTasks - values.numCompletedTasks);
	values.set(newValues);

	stateChanged();
  };

  public GlobalMeshProgress(final Collection<ObservableMeshProgress> meshProgresses) {

	this.meshProgresses.putAll(
			meshProgresses.stream().collect(Collectors.toMap(
					Function.identity(),
					m -> new MeshProgressValues(m.getNumTasks(), m.getNumCompletedTasks())
			))
	);

	this.numTasks.set(this.meshProgresses.values().stream().mapToInt(m -> m.numTasks).sum());
	this.numCompletedTasks.set(this.meshProgresses.values().stream().mapToInt(m -> m.numCompletedTasks).sum());

	this.meshProgresses.keySet().forEach(m -> m.addListener(this.updateListener));
  }
}
