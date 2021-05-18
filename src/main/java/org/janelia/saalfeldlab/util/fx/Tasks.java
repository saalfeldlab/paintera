package org.janelia.saalfeldlab.util.fx;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Tasks {

  private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
		  .setDaemon(true)
		  .setNameFormat("task-thread-%d")
		  .build();
  public static final ExecutorService TASK_SERVICE = Executors.newCachedThreadPool(THREAD_FACTORY);

  private Tasks() {

  }

public static <T> UtilityTask<T> createTask(Function<UtilityTask<T>, T> call) {

	return new UtilityTask<>(call);
  }

  public static void submit(Task<?> task) {

	TASK_SERVICE.submit(task);
  }

  public static class UtilityTask<V> extends Task<V> {

	private static final Logger LOG = LoggerFactory.getLogger(UtilityTask.class);
	final private Function<UtilityTask<V>, V> onCall;

	private UtilityTask(Function<UtilityTask<V>, V> call) {

	  super();
	  this.onCall = call;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Exposed to public so it can be used with the Tasks utility.
	 *
	 * @param value the value we are updating
	 */
	@Override public void updateValue(V value) {

	  super.updateValue(value);
	}

	@Override protected V call() {

	  try {
		return this.onCall.apply(this);
	  } catch (RuntimeException e) {
	    if (isCancelled()) {
	      LOG.debug("Task was cancelled");
		  return null;
		}
	    e.printStackTrace();
		throw e;
	  }
	}

	public UtilityTask<V> onSuccess(BiConsumer<WorkerStateEvent, UtilityTask<V>> onSuccess) {
	  this.setOnSucceeded(event -> onSuccess.accept(event, this));

	  return this;
	}


	public UtilityTask<V> onFailed(BiConsumer<WorkerStateEvent, UtilityTask<V>> onFailed) {
	  this.setOnFailed(event -> onFailed.accept(event, this));

	  return this;
	}


	public UtilityTask<V> onCancelled(BiConsumer<WorkerStateEvent, UtilityTask<V>> onCancelled) {
	  this.setOnCancelled(event -> onCancelled.accept(event, this));

	  return this;
	}

	public UtilityTask<V> onEnd(Consumer<UtilityTask<V>> onEnd) {
	  this.stateProperty().addListener((obs, oldv, newv) -> {
		switch (newv) {
		case SUCCEEDED:
		case CANCELLED:
		case FAILED:
		  onEnd.accept(this);
		default:
		  break;
		}
	  });
	  return this;
	}

	public void submit(ExecutorService executorService) {
	  executorService.submit(this);
	}

	public void submit() {
	  submit(TASK_SERVICE);
	}
  }
}
