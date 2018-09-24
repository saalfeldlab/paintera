package org.janelia.saalfeldlab.util;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class Sets {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final TLongHashSet containedInFirstButNotInSecond(
			final TLongHashSet first,
			final TLongHashSet second)
	{
		final TLongHashSet notInSecond = new TLongHashSet();
		for (final TLongIterator fIt = first.iterator(); fIt.hasNext(); )
		{
			final long p = fIt.next();
			if (!second.contains(p))
			{
				notInSecond.add(p);
			}
		}
		LOG.debug("First:         {}", first);
		LOG.debug("Second:        {}", second);
		LOG.debug("Not in second: {}", notInSecond);
		return notInSecond;
	}
}
