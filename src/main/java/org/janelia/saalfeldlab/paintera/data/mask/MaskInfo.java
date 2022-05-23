package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.type.numeric.integer.UnsignedLongType;

public class MaskInfo {

  public final int time;

  public final int level;

  public final UnsignedLongType value;

  public MaskInfo(final int time, final int level, final UnsignedLongType value) {

	super();
	this.time = time;
	this.level = level;
	this.value = value;
  }

  public MaskInfo copy() {

	return new MaskInfo(time, level, value);
  }

  @Override
  public String toString() {

	return String.format("{time=%d, level=%d, val=%s}", time, level, value);
  }
}
