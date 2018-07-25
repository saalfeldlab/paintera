package org.janelia.saalfeldlab.paintera.control.assignment;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.map.TLongLongMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.type.label.Label;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FragmentSegmentAssignmentOnlyLocal extends FragmentSegmentAssignmentStateWithActionTracker
{

	public interface Persister
	{
		public void persist(long[] keys, long[] values) throws UnableToPersist;
	}

	public static class DoesNotPersist implements Persister
	{

		@Override
		public void persist(final long[] keys, final long[] values) throws UnableToPersist
		{
			throw new UnableToPersist("Cannot persist at all!");
		}

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

	public FragmentSegmentAssignmentOnlyLocal(final Persister persister)
	{
		this(() -> new TLongLongHashMap(), persister);
	}

	public FragmentSegmentAssignmentOnlyLocal(
			final Supplier<TLongLongMap> initialLut,
			final Persister persister)
	{

		super();

		this.initialLut = initialLut;
		this.persister = persister;
		LOG.debug("Assignment map: {}", fragmentToSegmentMap);
		// TODO should reset lut also forget about all actions? I think not.
		resetLut();
	}

	@Override
	public synchronized void persist() throws UnableToPersist
	{
		if (actions.size() == 0)
		{
			LOG.debug("No actions to commit.");
			return;
		}

		try
		{
			// TODO Should we reset the LUT first to make sure that all previous
			// changes were loaded?
			LOG.debug("Persisting assignment {}", this.fragmentToSegmentMap);
			LOG.debug("Committing actions {}", this.actions);
			this.persister.persist(this.fragmentToSegmentMap.keys(), this.fragmentToSegmentMap.values());
			this.actions.clear();
		} catch (final Exception e)
		{
			throw e instanceof UnableToPersist ? (UnableToPersist) e : new UnableToPersist(e);
		}
	}

	@Override
	public synchronized long getSegment(final long fragmentId)
	{
		final long id;
		final long segmentId = fragmentToSegmentMap.get(fragmentId);
		if (segmentId == fragmentToSegmentMap.getNoEntryValue())
		{
			id = fragmentId;
		}
		else
		{
			id = segmentId;
		}
		LOG.debug("Returning {} for fragment {}: ", id, fragmentId);
		return id;
	}

	@Override
	public synchronized TLongHashSet getFragments(final long segmentId)
	{
		final TLongHashSet fragments = segmentToFragmentsMap.get(segmentId);
		return fragments == null ? new TLongHashSet(new long[] {segmentId}) : new TLongHashSet(fragments);
	}

	private void detachFragmentImpl(final Detach detach)
	{
		LOG.debug("Detach {}", detach);
		final long segmentFrom = fragmentToSegmentMap.get(detach.fragmentId);
		if (fragmentToSegmentMap.get(detach.fragmentFrom) != segmentFrom)
		{
			LOG.debug("{} not in same segment -- return without detach", detach);
			return;
		}

		final long fragmentId   = detach.fragmentId;
		final long fragmentFrom = detach.fragmentFrom;

		this.fragmentToSegmentMap.remove(fragmentId);

		LOG.debug("Removing fragment={} from segment={}", fragmentId, segmentFrom);
		final TLongHashSet fragments = this.segmentToFragmentsMap.get(segmentFrom);
		if (fragments != null)
		{
			fragments.remove(fragmentId);
			if (fragments.size() == 1)
			{
				this.fragmentToSegmentMap.remove(fragmentFrom);
				this.segmentToFragmentsMap.remove(segmentFrom);
			}
		}
	}

	private void mergeFragmentsImpl(final Merge merge)
	{

		LOG.debug("Merging {}", merge);

		final long into        = merge.intoFragmentId;
		final long from        = merge.fromFragmentId;
		final long segmentInto = merge.segmentId;

		LOG.trace("Current fragmentToSegmentMap {}", fragmentToSegmentMap);

		// If neither from nor into are assigned to a segment yet, both will
		// return fragmentToSegmentMap.getNoEntryKey() and we will falsely
		// return here
		// Therefore, check if from is contained. Alternatively, compare
		// getSegment( from ) == getSegment( to )
		if (fragmentToSegmentMap.contains(from) && fragmentToSegmentMap.get(from) == fragmentToSegmentMap.get(into))
		{
			LOG.debug("Fragments already in same segment -- not merging");
			return;
		}

		final long         segmentFrom   = fragmentToSegmentMap.contains(from) ? fragmentToSegmentMap.get(from) : from;
		final TLongHashSet fragmentsFrom = segmentToFragmentsMap.remove(segmentFrom);
		LOG.debug("From segment: {} To segment: {}", segmentFrom, segmentInto);

		if (!fragmentToSegmentMap.contains(into))
		{
			LOG.debug("Adding segment {} to framgent {}", segmentInto, into);
			fragmentToSegmentMap.put(into, segmentInto);
		}

		if (!segmentToFragmentsMap.contains(segmentInto))
		{
			final TLongHashSet fragmentOnly = new TLongHashSet();
			fragmentOnly.add(into);
			LOG.debug("Adding fragments {} for segmentInto {}", fragmentOnly, segmentInto);
			segmentToFragmentsMap.put(segmentInto, fragmentOnly);
		}
		LOG.debug("Framgents for from segment: {}", fragmentsFrom);

		if (fragmentsFrom != null)
		{
			final TLongHashSet fragmentsInto = segmentToFragmentsMap.get(segmentInto);
			LOG.debug("Fragments into {}", fragmentsInto);
			fragmentsInto.addAll(fragmentsFrom);
			Arrays.stream(fragmentsFrom.toArray()).forEach(id -> fragmentToSegmentMap.put(id, segmentInto));
		}
		else
		{
			segmentToFragmentsMap.get(segmentInto).add(from);
			fragmentToSegmentMap.put(from, segmentInto);
		}
	}

	private void resetLut()
	{
		fragmentToSegmentMap.clear();
		fragmentToSegmentMap.putAll(initialLut.get());
		syncILut();

		this.actions.forEach(this::apply);

	}

	@Override
	protected void applyImpl(final AssignmentAction action)
	{
		LOG.debug("Applying action {}", action);
		switch (action.getType())
		{
			case MERGE:
			{
				LOG.debug("Applying merge {}", action);
				mergeFragmentsImpl((Merge) action);
				break;
			}
			case DETACH:
				LOG.debug("Applying detach {}", action);
				detachFragmentImpl((Detach) action);
				break;
		}
	}

	private synchronized void syncILut()
	{
		segmentToFragmentsMap.clear();
		final TLongLongIterator lutIterator = fragmentToSegmentMap.iterator();
		while (lutIterator.hasNext())
		{
			lutIterator.advance();
			final long   fragmentId = lutIterator.key();
			final long   segmentId  = lutIterator.value();
			TLongHashSet fragments  = segmentToFragmentsMap.get(segmentId);
			if (fragments == null)
			{
				fragments = new TLongHashSet();
				fragments.add(segmentId);
				segmentToFragmentsMap.put(segmentId, fragments);
			}
			fragments.add(fragmentId);
		}
	}

	public int size()
	{
		return this.fragmentToSegmentMap.size();
	}

	public void persist(final long[] keys, final long[] values)
	{
		this.fragmentToSegmentMap.keys(keys);
		this.fragmentToSegmentMap.values(values);
	}

	@Override
	public Optional<Merge> getMergeAction(
			final long from,
			final long into,
			final LongSupplier newSegmentId)
	{
		if (from == into)
		{
			LOG.debug("fragments {} {} are the same -- no action necessary", from, into);
			return Optional.empty();
		}

		if (getSegment(from) == getSegment(into))
		{
			LOG.debug(
					"fragments {} {} are in the same segment {} {} -- no action necessary",
					from,
					into,
					getSegment(from),
					getSegment(into)
			         );
			return Optional.empty();
		}

		// TODO do not add to fragmentToSegmentMap here. Have the mergeImpl take
		// care of it instead.
		if (getSegment(into) == into)
		{
			fragmentToSegmentMap.put(into, newSegmentId.getAsLong());
		}

		final Merge merge = new Merge(from, into, fragmentToSegmentMap.get(into));
		return Optional.of(merge);
	}

	@Override
	public Optional<Detach> getDetachAction(final long fragmentId, final long from)
	{

		if (fragmentId == from)
		{
			LOG.debug("{} and {} ar the same -- no action necessary", fragmentId, from);
			return Optional.empty();
		}

		return Optional.ofNullable(new Detach(fragmentId, from));

	}

}
