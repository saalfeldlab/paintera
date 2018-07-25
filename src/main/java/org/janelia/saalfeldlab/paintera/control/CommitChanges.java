package org.janelia.saalfeldlab.paintera.control;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.janelia.saalfeldlab.paintera.data.mask.CannotPersist;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitChanges
{

	public enum Commitable
	{
		CANVAS,
		FRAGMENT_SEGMENT_ASSIGNMENTS;

		public static Set<Commitable> asSet()
		{
			return setOf(Commitable.values());
		}

		public static Set<Commitable> setOf(final Commitable... commitables)
		{
			return new HashSet<>(Arrays.asList(commitables));
		}

	}

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void commit(
			final Function<Collection<Commitable>, Optional<Set<Commitable>>> getCommitablesToCommit,
			final SourceState<?, ?> currentState) throws CannotPersist, UnableToPersist
	{
		commit(getCommitablesToCommit, currentState, Optional.empty());
	}

	public static void commit(
			final Function<Collection<Commitable>, Optional<Set<Commitable>>> getCommitablesToCommit,
			final SourceState<?, ?> currentState,
			final Optional<Set<Commitable>> filteredCommitableOptions) throws CannotPersist, UnableToPersist
	{
		LOG.debug("Commiting for state {}", currentState);
		if (currentState == null || !(currentState instanceof LabelSourceState<?, ?>))
		{
			LOG.debug("Invalid current state (null or not {}): {}", LabelSourceState.class, currentState);
			return;
		}
		final LabelSourceState<?, ?> state             = (LabelSourceState<?, ?>) currentState;
		final boolean                isMasked          = state.getDataSource() instanceof MaskedSource<?, ?>;
		final Set<Commitable>        commitableOptions = filteredCommitableOptions.orElseGet(Commitable::asSet);
		if (!isMasked)
		{
			commitableOptions.remove(Commitable.CANVAS);
		}

		final Optional<Set<Commitable>> commitables = getCommitablesToCommit.apply(commitableOptions);

		if (!commitables.isPresent())
		{
			LOG.debug("Not commiting anything.");
			return;
		}

		LOG.debug("Commiting {}", commitables.get());
		for (final Commitable commitable : commitables.get())
		{
			switch (commitable)
			{
				case CANVAS:
					if (isMasked)
					{
						// TODO Should this be handled here instead of throwing
						// exception?
						((MaskedSource<?, ?>) state.getDataSource()).persistCanvas();
					}
					break;
				case FRAGMENT_SEGMENT_ASSIGNMENTS:
				{
					state.assignment().persist();
					break;
				}
			}

		}
	}

}
