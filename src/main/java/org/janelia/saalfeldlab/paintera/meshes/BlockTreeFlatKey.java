package org.janelia.saalfeldlab.paintera.meshes;

public class BlockTreeFlatKey {

  public final int scaleLevel;
  public final long blockIndex;

  BlockTreeFlatKey(final int scaleLevel, final long blockIndex) {

	this.scaleLevel = scaleLevel;
	this.blockIndex = blockIndex;
  }

  @Override
  public String toString() {

	return String.format("{scaleLevel=%d, blockIndex=%d}", scaleLevel, blockIndex);
  }

  @Override
  public int hashCode() {

	return 31 * scaleLevel + Long.hashCode(blockIndex);
  }

  @Override
  public boolean equals(final Object obj) {

	if (super.equals(obj))
	  return true;

	if (obj instanceof BlockTreeFlatKey) {
	  final BlockTreeFlatKey other = (BlockTreeFlatKey)obj;
	  return scaleLevel == other.scaleLevel && blockIndex == other.blockIndex;
	}

	return false;
  }
}
