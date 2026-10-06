package org.janelia.saalfeldlab.paintera.control.assignment;

import com.google.gson.annotations.Expose;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import io.github.oshai.kotlinlogging.KLogger;
import io.github.oshai.kotlinlogging.KotlinLogging;
import javafx.util.Pair;
import kotlin.Unit;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.jctools.maps.NonBlockingHashMapLong;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class FragmentSegmentAssignmentOnlyLocal extends FragmentSegmentAssignmentStateWithActionTracker {

	public interface Persister {

		void persist(long[] keys, long[] values) throws UnableToPersist;

		/**
		 * @return {@code false} if {@link #persist} can never succeed.
		 * e.g. because the source is not backed by a Paintera group.
		 */
		default boolean canPersist() {

			return true;
		}

		/**
		 * @return why this cannot persist, or {@code null} if it can
		 */
		default String getPersistError() {

			return null;
		}
	}

	public static class DoesNotPersist implements Persister {

		@Expose
		private final String persistError;

		public DoesNotPersist() {

			this("Cannot persist at all!");
		}

		public DoesNotPersist(final String persistError) {

			this.persistError = persistError;
		}

		@Override
		public void persist(final long[] keys, final long[] values) throws UnableToPersist {

			throw new UnableToPersist(this.persistError);
		}

		@Override
		public boolean canPersist() {

			return false;
		}

		@Override
		public String getPersistError() {

			return this.persistError;
		}

	}

	public static class NoInitialLutAvailable implements Supplier<TLongLongMap> {

		@Override
		public TLongLongMap get() {

			return new TLongLongHashMap();
		}
	}

	public static Supplier<TLongLongMap> NO_INITIAL_LUT_AVAILABLE = new NoInitialLutAvailable();

	public static Persister doesNotPersist(final String persistError) {

		return new DoesNotPersist(persistError);
	}

	private static final KLogger LOG = KotlinLogging.INSTANCE.logger(() -> Unit.INSTANCE);

	private volatile NonBlockingHashMapLong<Long> fragmentToSegmentMap = new NonBlockingHashMapLong<>(false);

	private volatile NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap = new NonBlockingHashMapLong<>(false);

	private final Object writeLock = new Object();

	private final Persister persister;

	private final Supplier<TLongLongMap> initialLut;

	public FragmentSegmentAssignmentOnlyLocal(final Persister persister) {

		this(NO_INITIAL_LUT_AVAILABLE, persister);
	}

	public FragmentSegmentAssignmentOnlyLocal(
			final Supplier<TLongLongMap> initialLut,
			final Persister persister) {

		super();

		this.initialLut = initialLut;
		this.persister = persister;
		LOG.debug("Assignment map: {}", fragmentToSegmentMap);
		// TODO should reset lut also forget about all actions? I think not.
		resetLut();
	}

	public Persister getPersister() {

		return this.persister;
	}

	public Supplier<TLongLongMap> getInitialLutSupplier() {

		return this.initialLut;
	}

	@Override
	public boolean hasPersistableData() {

		return persister.canPersist() && super.hasPersistableData();
	}

	@Override
	public synchronized void persist() throws UnableToPersist {

		if (actions.size() == 0) {
			LOG.debug("No actions to commit.");
			return;
		}

		try {
			// TODO Should we reset the LUT first to make sure that all previous changes were loaded?
			LOG.debug("Persisting assignment {}", this.fragmentToSegmentMap);
			LOG.debug("Committing actions {}", this.actions);
            int numEntries = this.fragmentToSegmentMap.size();
            final long[] keys = new long[numEntries];
			final long[] values = new long[numEntries];
            var i = 0;
			for (final Map.Entry<Long, Long> entry : this.fragmentToSegmentMap.entrySet()) {
				keys[i] = entry.getKey();
				values[i++] = entry.getValue();
			}
			this.persister.persist(keys, values);
			this.actions.clear();
		} catch (final Exception e) {
			throw e instanceof UnableToPersist ? (UnableToPersist)e : new UnableToPersist(e);
		}
	}

	@Override
	public long getSegment(final long fragmentId) {


        Long segmentId = fragmentToSegmentMap.get(fragmentId);
        final long id = segmentId == null ? fragmentId : segmentId;
        LOG.trace(() -> "Returning %s for fragment %s: ".formatted(id, fragmentId));
		return id;
	}

	@Override
	public TLongHashSet getFragments(final long segmentId) {

        final TLongHashSet fragments = segmentToFragmentsMap.get(segmentId);
		return fragments == null ? new TLongHashSet(new long[]{segmentId}) : new TLongHashSet(fragments);
	}

	/**
	 *  Get a write-safe TLongHashSet of fragments for a segment.
     *  If `applyInPlace` then the active reference from {@code segmentToFragmentsMap} is returned.
     *  Otherwise, a copy is returned.
	 */
	private static TLongHashSet modifiableFragmentsSet(
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap,
			final long segment,
			final boolean applyInPlace) {

		final TLongHashSet fragments = segmentToFragmentsMap.get(segment);
        if (fragments == null)
            return null;

        return applyInPlace ? fragments : new TLongHashSet(fragments);
    }

	private static void detachFragmentImpl(
			final NonBlockingHashMapLong<Long> fragmentToSegmentMap,
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap,
			final Detach detach,
			final boolean applyInPlace) {

		LOG.debug("Detach {}", detach);
		final Long segmentFrom = fragmentToSegmentMap.get(detach.fragmentId);
		if (!Objects.equals(fragmentToSegmentMap.get(detach.fragmentFrom), segmentFrom)) {
			LOG.debug("{} not in same segment -- return without detach", detach);
			return;
		}

		final long fragmentId = detach.fragmentId;
		final long fragmentFrom = detach.fragmentFrom;

		fragmentToSegmentMap.remove(fragmentId);
		LOG.debug("Removed {} from {}", fragmentId, fragmentToSegmentMap);

		LOG.debug("Removing fragment={} from segment={}", fragmentId, segmentFrom);
		final TLongHashSet fragments = segmentFrom == null ? null : modifiableFragmentsSet(segmentToFragmentsMap, segmentFrom, applyInPlace);
		if (fragments != null) {
			fragments.remove(fragmentId);
			LOG.debug("Removed {} from {}", fragmentId, fragments);
			if (fragments.isEmpty()) {
				fragmentToSegmentMap.remove(fragmentFrom);
				segmentToFragmentsMap.remove((long)segmentFrom);
			} else {
				segmentToFragmentsMap.put((long)segmentFrom, fragments);
			}
		}
		LOG.debug("Fragment-to-segment map after detach: {}", fragmentToSegmentMap);
		LOG.debug("Segment-to-fragment map after detach: {}", segmentToFragmentsMap);
	}

	private static void mergeFragmentsImpl(
			final NonBlockingHashMapLong<Long> fragmentToSegmentMap,
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap,
			final Merge merge,
			final boolean applyInPlace) {

		LOG.debug("Merging {}", merge);

		final long into = merge.intoFragmentId;
		final long from = merge.fromFragmentId;
		final long segmentInto = merge.segmentId;

		LOG.trace("Current fragmentToSegmentMap {}", fragmentToSegmentMap);

		// If neither from nor into are assigned to a segment yet, both will
		// return fragmentToSegmentMap.getNoEntryKey() and we will falsely
		// return here
		// Therefore, check if from is contained. Alternatively, compare
		// getSegment( from ) == getSegment( to )
		if (fragmentToSegmentMap.containsKey(from) && Objects.equals(fragmentToSegmentMap.get(from), fragmentToSegmentMap.get(into))) {
			LOG.debug("Fragments already in same segment -- not merging");
			return;
		}

		final long segmentFrom = fragmentToSegmentMap.containsKey(from) ? fragmentToSegmentMap.get(from) : from;
		final TLongHashSet fragmentsFrom = segmentToFragmentsMap.remove(segmentFrom);
		LOG.debug("From segment: {} To segment: {}", segmentFrom, segmentInto);

		if (!fragmentToSegmentMap.containsKey(into)) {
			LOG.debug("Adding segment {} to framgent {}", segmentInto, into);
			fragmentToSegmentMap.put(into, Long.valueOf(segmentInto));
		}

		if (!segmentToFragmentsMap.containsKey(segmentInto)) {
			final TLongHashSet fragmentOnly = new TLongHashSet();
			fragmentOnly.add(into);
			LOG.debug("Adding fragments {} for segmentInto {}", fragmentOnly, segmentInto);
			segmentToFragmentsMap.put(segmentInto, fragmentOnly);
		}
		LOG.debug("Framgents for from segment: {}", fragmentsFrom);

		if (fragmentsFrom != null) {
			final TLongHashSet fragmentsInto = modifiableFragmentsSet(segmentToFragmentsMap, segmentInto, applyInPlace);
			LOG.debug("Fragments into {}", fragmentsInto);
			fragmentsInto.addAll(fragmentsFrom);
			segmentToFragmentsMap.put(segmentInto, fragmentsInto);
			Arrays.stream(fragmentsFrom.toArray()).forEach(id -> fragmentToSegmentMap.put(id, Long.valueOf(segmentInto)));
		} else {
			final TLongHashSet fragmentsInto = modifiableFragmentsSet(segmentToFragmentsMap, segmentInto, applyInPlace);
			fragmentsInto.add(from);
			segmentToFragmentsMap.put(segmentInto, fragmentsInto);
			fragmentToSegmentMap.put(from, Long.valueOf(segmentInto));
		}
	}

	private void resetLut() {

		synchronized (writeLock) {
			/* build both maps in before updating the fields, so they aren't read until all action are applied.  */
			final NonBlockingHashMapLong<Long> fragmentToSegmentMap = new NonBlockingHashMapLong<>(false);
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap = new NonBlockingHashMapLong<>(false);
			final TLongLongIterator fragSegIter = initialLut.get().iterator();
			while (fragSegIter.hasNext()) {
				fragSegIter.advance();
				final long fragment = fragSegIter.key();
				final long segment = fragSegIter.value();
				if (!Label.regular(fragment) || !Label.regular(segment)) {
					LOG.warn(() -> "Ignoring assignment with an irregular id: fragment=%s segment=%s".formatted(fragment, segment));
					continue;
				}
				fragmentToSegmentMap.put(fragment, Long.valueOf(segment));
			}
			syncILut(fragmentToSegmentMap, segmentToFragmentsMap);

			this.actions.stream()
                    .filter(p -> p.getValue().get())
                    .map(Pair::getKey)
					.forEach(action -> applyTo(fragmentToSegmentMap, segmentToFragmentsMap, action, true));

			/* no reader looks at both maps, so they do not have to become visible together */
			this.fragmentToSegmentMap = fragmentToSegmentMap;
			this.segmentToFragmentsMap = segmentToFragmentsMap;
		}
	}

	@Override
	protected void applyImpl(final AssignmentAction action) {

		synchronized (writeLock) {
			applyTo(fragmentToSegmentMap, segmentToFragmentsMap, action, false);
		}
	}

	private static void applyTo(
			final NonBlockingHashMapLong<Long> fragmentToSegmentMap,
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap,
			final AssignmentAction action,
			final boolean applyInPlace) {

		LOG.debug("Applying action {}", action);
		switch (action.getType()) {
		case MERGE: {
			LOG.debug("Applying merge {}", action);
			mergeFragmentsImpl(fragmentToSegmentMap, segmentToFragmentsMap, (Merge)action, applyInPlace);
			break;
		}
		case DETACH:
			LOG.debug("Applying detach {}", action);
			detachFragmentImpl(fragmentToSegmentMap, segmentToFragmentsMap, (Detach)action, applyInPlace);
			break;
		}
	}

	@Override
	protected void reapplyActions() {

		resetLut();
	}

	private static void syncILut(
			final NonBlockingHashMapLong<Long> fragmentToSegmentMap,
			final NonBlockingHashMapLong<TLongHashSet> segmentToFragmentsMap) {

		segmentToFragmentsMap.clear();
		for (final Map.Entry<Long, Long> lutEntry : fragmentToSegmentMap.entrySet()) {
			final long fragmentId = lutEntry.getKey();
			final long segmentId = lutEntry.getValue();
			TLongHashSet fragments = segmentToFragmentsMap.get(segmentId);
			if (fragments == null) {
				fragments = new TLongHashSet();
				fragments.add(segmentId);
				segmentToFragmentsMap.put(segmentId, fragments);
			}
			fragments.add(fragmentId);
		}
	}

	public int size() {

		return this.fragmentToSegmentMap.size();
	}

	public void persist(final long[] keys, final long[] values) {

		int i = 0;
		for (final Map.Entry<Long, Long> entry : this.fragmentToSegmentMap.entrySet()) {
			keys[i] = entry.getKey();
			values[i++] = entry.getValue();
		}
	}

	@Override
	public Optional<Merge> getMergeAction(
			final long fragment1,
			final long fragment2,
			final LongSupplier newSegmentId) {

		if (fragment1 == fragment2) {
			LOG.debug("fragments {} {} are the same -- no action necessary", fragment1, fragment2);
			return Optional.empty();
		}

		if (!Label.regular(fragment1) || !Label.regular(fragment2)) {
			LOG.warn(() -> "Cannot merge an irregular id: %s %s".formatted(fragment1, fragment2));
			return Optional.empty();
		}

		if (getSegment(fragment1) == getSegment(fragment2)) {
			LOG.debug(
					"fragments {} {} are in the same segment {} {} -- no action necessary",
					fragment1,
					fragment2,
					getSegment(fragment1),
					getSegment(fragment2)
			);
			return Optional.empty();
		}

		final long fromFragmentId;
		final long intoFragmentId;
		{
			long fromSegment = getSegment(fragment1);
			if (fromSegment == fragment1)
				fromSegment = Label.INVALID;
			long intoSegment = getSegment(fragment2);
			if (intoSegment == fragment2)
				intoSegment = Label.INVALID;

			if (intoSegment >= fromSegment) {
				intoFragmentId = fragment2;
				fromFragmentId = fragment1;
			} else {
				intoFragmentId = fragment1;
				fromFragmentId = fragment2;
			}
		}

		// TODO do not add to fragmentToSegmentMap here. Have the mergeImpl take care of it instead.
		synchronized (writeLock) {
			if (getSegment(intoFragmentId) == intoFragmentId) {
				final long newSegment = newSegmentId.getAsLong();
				if (!Label.regular(newSegment)) {
					LOG.warn(() -> "Cannot merge into an irregular segment id: %s".formatted(newSegment));
					return Optional.empty();
				}
				fragmentToSegmentMap.put(intoFragmentId, Long.valueOf(newSegment));
			}

			final Merge merge = new Merge(fromFragmentId, intoFragmentId, fragmentToSegmentMap.get(intoFragmentId));
			return Optional.of(merge);
		}
	}

	@Override
	public Optional<Detach> getDetachAction(final long fragmentId, final long from) {

		return Optional.of(new Detach(fragmentId, from));

	}

	@Override
	public boolean isSegmentConsistent(final long segmentId, final TLongSet containedFragments) {

		final TLongHashSet actualFragments = segmentToFragmentsMap.get(segmentId);
		// if actualFragments is null, no assignment available for fragment/segment, that means
		// fragmentId == segmentId and fragmentId is the only fragment in this segmet.
		if (actualFragments == null)
			return containedFragments.size() == 1 && containedFragments.contains(segmentId);
		return actualFragments.equals(containedFragments);
	}

}
