package org.janelia.saalfeldlab.paintera.data.mask.persist;

import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public interface PersistCanvas {

	void persistCanvas(CachedCellImg<UnsignedLongType, ?> canvas, long[] blockIds) throws UnableToPersistCanvas;

	default void updateLabelBlockLookup(CachedCellImg<UnsignedLongType, ?> canvas, long[] blockIds) throws UnableToUpdateLabelBlockLookup
	{
		throw new LabelBlockLookupUpdateNotSupported("");
	}

	default boolean supportsLabelBlockLookupUpdate()
	{
		return false;
	}
}
