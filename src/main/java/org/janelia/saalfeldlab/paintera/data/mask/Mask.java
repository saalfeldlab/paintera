package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Invalidate;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.volatiles.VolatileUnsignedLongType;

public interface Mask {

  // TODO should this also hold the voatile RAI?
  MaskInfo getInfo();

  RandomAccessibleInterval<UnsignedLongType> getRai();

  RandomAccessibleInterval<VolatileUnsignedLongType> getVolatileRai();

  Invalidate<?> getInvalidate();

  Invalidate<?> getInvalidateVolatile();

  Runnable getShutdown();

}
