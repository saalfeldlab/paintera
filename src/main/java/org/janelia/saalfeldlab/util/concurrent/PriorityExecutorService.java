package org.janelia.saalfeldlab.util.concurrent;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public interface PriorityExecutorService<P extends Comparable<? super P>> extends ExecutorService {

  Future<?> submit(Runnable task, P priority);

  <T> Future<T> submit(Runnable task, T result, P priority);

  <T> Future<T> submit(Callable<T> task, P priority);

  <T> List<Future<T>> submitAll(List<Runnable> tasks, List<T> results, List<P> priorities);

  <T> List<Future<T>> submitAll(List<Callable<T>> tasks, List<P> priorities);
}
