package org.janelia.saalfeldlab.paintera.control.assignment;

import com.google.gson.annotations.Expose;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import javafx.util.Pair;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class FragmentSegmentAssignmentOnlyLocal extends FragmentSegmentAssignmentStateWithActionTracker {

	public interface Persister {

		void persist(long[] keys, long[] values) throws UnableToPersist;
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

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final TLongLongHashMap fragmentToSegmentMap = new TLongLongHashMap(
			Constants.DEFAULT_CAPACITY,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.TRANSPARENT,
			Label.TRANSPARENT
	);

	private final TLongObjectHashMap<TLongHashSet> segmentToFragmentsMap = new TLongObjectHashMap<>(
			Constants.DEFAULT_CAPACITY,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.TRANSPARENT
	);

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
	public synchronized void persist() throws UnableToPersist {

		if (actions.size() == 0) {
			LOG.debug("No actions to commit.");
			return;
		}

		try {
			// TODO Should we reset the LUT first to make sure that all previous changes were loaded?
			LOG.debug("Persisting assignment {}", this.fragmentToSegmentMap);
			LOG.debug("Committing actions {}", this.actions);
			this.persister.persist(this.fragmentToSegmentMap.keys(), this.fragmentToSegmentMap.values());
			this.actions.clear();
		} catch (final Exception e) {
			throw e instanceof UnableToPersist ? (UnableToPersist)e : new UnableToPersist(e);
		}
	}

	@Override
	public long getSegment(final long fragmentId) {

		final long id;
		final long segmentId = fragmentToSegmentMap.get(fragmentId);
		if (segmentId == fragmentToSegmentMap.getNoEntryValue()) {
			id = fragmentId;
		} else {
			id = segmentId;
		}
		if (LOG.isTraceEnabled()) {
			LOG.trace("Returning {} for fragment {}: ", id, fragmentId);
		}
		return id;
	}

	@Override
	public synchronized TLongHashSet getFragments(final long segmentId) {

		final TLongHashSet fragments = segmentToFragmentsMap.get(segmentId);
		return fragments == null ? new TLongHashSet(new long[]{segmentId}) : new TLongHashSet(fragments);
	}

	private void detachFragmentImpl(final Detach detach) {

		LOG.debug("Detach {}", detach);
		final long segmentFrom = fragmentToSegmentMap.get(detach.fragmentId);
		if (fragmentToSegmentMap.get(detach.fragmentFrom) != segmentFrom) {
			LOG.debug("{} not in same segment -- return without detach", detach);
			return;
		}

		final long fragmentId = detach.fragmentId;
		final long fragmentFrom = detach.fragmentFrom;

		this.fragmentToSegmentMap.remove(fragmentId);
		LOG.debug("Removed {} from {}", fragmentId, this.fragmentToSegmentMap);

		LOG.debug("Removing fragment={} from segment={}", fragmentId, segmentFrom);
		final TLongHashSet fragments = this.segmentToFragmentsMap.get(segmentFrom);
		if (fragments != null) {
			fragments.remove(fragmentId);
			LOG.debug("Removed {} from {}", fragmentId, fragments);
			if (fragments.isEmpty()) {
				this.fragmentToSegmentMap.remove(fragmentFrom);
				this.segmentToFragmentsMap.remove(segmentFrom);
			}
		}
		LOG.debug("Fragment-to-segment map after detach: {}", this.fragmentToSegmentMap);
		LOG.debug("Segment-to-fragment map after detach: {}", this.segmentToFragmentsMap);
	}

	private void mergeFragmentsImpl(final Merge merge) {

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
		if (fragmentToSegmentMap.contains(from) && fragmentToSegmentMap.get(from) == fragmentToSegmentMap.get(into)) {
			LOG.debug("Fragments already in same segment -- not merging");
			return;
		}

		final long segmentFrom = fragmentToSegmentMap.contains(from) ? fragmentToSegmentMap.get(from) : from;
		final TLongHashSet fragmentsFrom = segmentToFragmentsMap.remove(segmentFrom);
		LOG.debug("From segment: {} To segment: {}", segmentFrom, segmentInto);

		if (!fragmentToSegmentMap.contains(into)) {
			LOG.debug("Adding segment {} to framgent {}", segmentInto, into);
			fragmentToSegmentMap.put(into, segmentInto);
		}

		if (!segmentToFragmentsMap.contains(segmentInto)) {
			final TLongHashSet fragmentOnly = new TLongHashSet();
			fragmentOnly.add(into);
			LOG.debug("Adding fragments {} for segmentInto {}", fragmentOnly, segmentInto);
			segmentToFragmentsMap.put(segmentInto, fragmentOnly);
		}
		LOG.debug("Framgents for from segment: {}", fragmentsFrom);

		if (fragmentsFrom != null) {
			final TLongHashSet fragmentsInto = segmentToFragmentsMap.get(segmentInto);
			LOG.debug("Fragments into {}", fragmentsInto);
			fragmentsInto.addAll(fragmentsFrom);
			Arrays.stream(fragmentsFrom.toArray()).forEach(id -> fragmentToSegmentMap.put(id, segmentInto));
		} else {
			segmentToFragmentsMap.get(segmentInto).add(from);
			fragmentToSegmentMap.put(from, segmentInto);
		}
	}

	private void resetLut() {

		fragmentToSegmentMap.clear();
		fragmentToSegmentMap.putAll(initialLut.get());
		syncILut();

		this.actions.stream().filter(p -> p.getValue().get()).map(Pair::getKey).forEach(this::applyImpl);

	}

	@Override
	protected void applyImpl(final AssignmentAction action) {

		LOG.debug("Applying action {}", action);
		switch (action.getType()) {
		case MERGE: {
			LOG.debug("Applying merge {}", action);
			mergeFragmentsImpl((Merge)action);
			break;
		}
		case DETACH:
			LOG.debug("Applying detach {}", action);
			detachFragmentImpl((Detach)action);
			break;
		}
	}

	@Override
	protected void reapplyActions() {

		resetLut();
	}

	private synchronized void syncILut() {

		segmentToFragmentsMap.clear();
		final TLongLongIterator lutIterator = fragmentToSegmentMap.iterator();
		while (lutIterator.hasNext()) {
			lutIterator.advance();
			final long fragmentId = lutIterator.key();
			final long segmentId = lutIterator.value();
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

		this.fragmentToSegmentMap.keys(keys);
		this.fragmentToSegmentMap.values(values);
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
		if (getSegment(intoFragmentId) == intoFragmentId) {
			fragmentToSegmentMap.put(intoFragmentId, newSegmentId.getAsLong());
		}

		final Merge merge = new Merge(fromFragmentId, intoFragmentId, fragmentToSegmentMap.get(intoFragmentId));
		return Optional.of(merge);
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
