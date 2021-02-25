package org.janelia.saalfeldlab.paintera.cache;

import net.imglib2.cache.Invalidate;

import java.util.function.Predicate;

public class NoOpInvalidate<K> implements Invalidate<K> {

  @Override
  public void invalidate(K key) {

  }

  @Override
  public void invalidateIf(long parallelismThreshold, Predicate<K> condition) {

  }

  @Override
  public void invalidateAll(long parallelismThreshold) {

  }
}
