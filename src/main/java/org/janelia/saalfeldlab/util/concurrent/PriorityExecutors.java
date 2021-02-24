package org.janelia.saalfeldlab.util.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PriorityExecutors {

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPriorityFixedThreadPool(final int nThreads) {

	return new PriorityThreadPoolExecutor<>(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS);
  }

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPriorityFixedThreadPool(final int nThreads, final ThreadFactory threadFactory) {

	return new PriorityThreadPoolExecutor<>(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, threadFactory);
  }

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPrioritySingleThreadPool() {

	return new PriorityThreadPoolExecutor<>(1, 1, 0L, TimeUnit.MILLISECONDS);
  }

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPrioritySingleThreadPool(final ThreadFactory threadFactory) {

	return new PriorityThreadPoolExecutor<>(1, 1, 0L, TimeUnit.MILLISECONDS, threadFactory);
  }

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPriorityCachedThreadPool() {

	return new PriorityThreadPoolExecutor<>(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS);
  }

  public static <P extends Comparable<? super P>> PriorityExecutorService<P> newPriorityCachedThreadPool(final ThreadFactory threadFactory) {

	return new PriorityThreadPoolExecutor<>(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, threadFactory);
  }
}
