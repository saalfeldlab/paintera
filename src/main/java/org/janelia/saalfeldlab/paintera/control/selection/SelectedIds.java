package org.janelia.saalfeldlab.paintera.control.selection;

import gnu.trove.TCollections;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.fx.ObservableWithListenersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

public class SelectedIds extends ObservableWithListenersList {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private final TLongHashSet selectedIds;

	private long lastSelection = Label.INVALID;

	public SelectedIds() {

		this(new TLongHashSet());
	}

	public SelectedIds(final TLongHashSet selectedIds) {

		super();
		this.selectedIds = selectedIds;
		updateLastSelection();
	}

	private <T> T withReadLock(Supplier<T> operation) {

		lock.readLock().lock();
		try {
			return operation.get();
		} finally {
			lock.readLock().unlock();
		}
	}

	private void withWriteLock(Runnable operation) {

		lock.writeLock().lock();
		try {
			operation.run();
		} finally {
			lock.writeLock().unlock();
		}
	}

	private boolean isActiveNoLock(long id) {

		return selectedIds.contains(id);
	}

	private void activateAlsoNoLock(final long... ids) {

		for (final long id : ids) {
			selectedIds.add(id);
		}
		if (ids.length > 0)
			this.lastSelection = ids[0];
	}

	private void deactivateAllNoLock() {

		selectedIds.clear();
		lastSelection = Label.INVALID;
	}

	public boolean isActive(final long id) {

		return withReadLock(() -> isActiveNoLock(id));
	}

	public void activate(final long... ids) {

		withWriteLock(() -> {
			deactivateAllNoLock();
			activateAlsoNoLock(ids);
			LOG.debug("Activated " + Arrays.toString(ids) + " " + selectedIds);
		});
		stateChanged();
	}

	public void activateAlso(final long... ids) {

		withWriteLock(() -> activateAlsoNoLock(ids));
		stateChanged();
	}

	public void deactivateAll() {

		withWriteLock(this::deactivateAllNoLock);
		stateChanged();
	}

	public void deactivate(final long... ids) {

		withWriteLock(() -> {
			for (final long id : ids) {
				selectedIds.remove(id);
				if (id == lastSelection)
					lastSelection = Label.INVALID;
			}
			LOG.debug("Deactivated {}, {}", Arrays.toString(ids), selectedIds);
		});
		stateChanged();
	}

	public boolean isOnlyActiveId(final long id) {

		return withReadLock(() -> selectedIds.size() == 1 && isActiveNoLock(id));
	}

	public TLongSet getActiveIds() {

		return withReadLock(() -> TCollections.unmodifiableSet(new TLongHashSet(this.selectedIds)));
	}

	public long[] getActiveIdsCopyAsArray() {

		return withReadLock(this.selectedIds::toArray);
	}

	public boolean isEmpty() {

		return withReadLock(this.selectedIds::isEmpty);
	}

	public long getLastSelection() {

		return withReadLock(() -> this.lastSelection);
	}

	public boolean isLastSelection(final long id) {

		return withReadLock(() -> this.lastSelection == id);
	}

	public boolean isLastSelectionValid() {

		return withReadLock(() -> this.lastSelection != Label.INVALID);
	}

	@Override
	public String toString() {

		return withReadLock(selectedIds::toString);
	}

	/**
	 * Returns a LongStream backed directly by the TLongHashSet.
	 * The stream holds a read lock during operation, blocking writers but allowing other readers.
	 * <p>
	 * WARNING: The read lock is held until the stream is fully consumed or closed.
	 * Long-running stream operations will block writers.
	 *
	 * @return LongStream that can be used sequentially or in parallel
	 */
	public LongStream stream() {

		return lockedStream(false);

	}

	/**
	 * Returns a parallel LongStream backed directly by the TLongHashSet.
	 * The stream holds a read lock during operation, blocking writers but allowing other readers.
	 * <p>
	 * WARNING: The read lock is held until the stream is fully consumed or closed.
	 * Long-running stream operations will block writers.
	 *
	 * @return Parallel LongStream
	 */
	public LongStream parallelStream() {

		return lockedStream(true);
	}

	private LongStream lockedStream(boolean parallel) {

		final var wrappedTroveLongIterator = new PrimitiveIterator.OfLong() {

			final TLongIterator tIterator = selectedIds.iterator();

			@Override public boolean hasNext() {

				return tIterator.hasNext();
			}

			@Override public long nextLong() {

				return tIterator.next();
			}
		};

		final Spliterator.OfLong spliterator = Spliterators.spliterator(
				wrappedTroveLongIterator,
				selectedIds.size(),
				Spliterator.SIZED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SUBSIZED | Spliterator.IMMUTABLE);

		lock.readLock().lock();
		try {
			return StreamSupport
					.longStream(spliterator, parallel)
					.onClose(lock.readLock()::unlock);
		} catch (final Exception e) {
			lock.readLock().unlock();
			throw e;
		}
	}

	private void updateLastSelection() {
		// Note: This method is called from constructor, so no locking needed
		// Also called from deactivateAll which already holds write lock
		if (!selectedIds.isEmpty()) {
			lastSelection = selectedIds.iterator().next();
		}
	}
}
