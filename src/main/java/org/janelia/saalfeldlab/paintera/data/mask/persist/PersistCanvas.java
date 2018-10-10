package org.janelia.saalfeldlab.paintera.data.mask.persist;

import gnu.trove.iterator.TLongIterator;
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

		private void createDiffIfNecessary()
		{
			if (!diffWasCalculated)
				createDiff();
		}

		private void createDiff()
		{
			this.wasAdded.clear();
			this.wasRemoved.clear();

			for (final TLongIterator newLabelIt = this.newUniqueLabels.iterator(); newLabelIt.hasNext();)
			{
				final long id = newLabelIt.next();
				if (!this.oldUniqueLabels.contains(id))
					this.wasAdded.add(id);
			}

			for (final TLongIterator oldLabelIt = this.oldUniqueLabels.iterator(); oldLabelIt.hasNext();)
			{
				final long id = oldLabelIt.next();
				if (!this.newUniqueLabels.contains(id))
					this.wasRemoved.add(id);
			}

			this.diffWasCalculated = true;
		}

		private long[] getIds(final TLongHashSet idSet)
		{
			createDiffIfNecessary();
			return idSet.toArray();
		}

		@Override
		public String toString()
		{
			return String.format("{BlockDiff: new=%s old=%s}", newUniqueLabels, oldUniqueLabels);
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
