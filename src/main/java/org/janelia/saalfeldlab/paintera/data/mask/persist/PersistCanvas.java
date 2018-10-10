package org.janelia.saalfeldlab.paintera.data.mask.persist;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.numeric.integer.UnsignedLongType;

import java.util.List;

public interface PersistCanvas {

	class BlockDiff
	{

		private final TLongHashSet oldUniqueLabels = new TLongHashSet();

		private final TLongHashSet newUniqueLabels = new TLongHashSet();

		private boolean diffWasCalculated = false;

		private final TLongHashSet wasAdded = new TLongHashSet();

		private final TLongHashSet wasRemoved = new TLongHashSet();

		public void addToOldUniqueLabels(long id) {
			this.oldUniqueLabels.add(id);
			invalidate();
		}

		public void addToNewUniqueLabels(long id) {
			this.newUniqueLabels.add(id);
			invalidate();
		}

		public long[] getNewUniqueIds()
		{
			return this.newUniqueLabels.toArray();
		}

		public long[] getAddedIds()
		{
			return getIds(this.wasAdded);
		}

		public long[] getRemovedIds()
		{
			return getIds(this.wasRemoved);
		}

		private void invalidate()
		{
			this.diffWasCalculated = false;
		}

		private void createDiff()
		{
			this.oldUniqueLabels.clear();
			this.newUniqueLabels.clear();
			this.diffWasCalculated = true;
			// TODO
		}

		private long[] getIds(final TLongHashSet idSet)
		{
			if (!diffWasCalculated)
				createDiff();
			return idSet.toArray();
		}

	}

	List<TLongObjectMap<BlockDiff>> persistCanvas(CachedCellImg<UnsignedLongType, ?> canvas, long[] blockIds) throws UnableToPersistCanvas;

	default void updateLabelBlockLookup(final List<TLongObjectMap<BlockDiff>> blockDiffs) throws UnableToUpdateLabelBlockLookup
	{
		throw new LabelBlockLookupUpdateNotSupported("");
	}

	default boolean supportsLabelBlockLookupUpdate()
	{
		return false;
	}
}
