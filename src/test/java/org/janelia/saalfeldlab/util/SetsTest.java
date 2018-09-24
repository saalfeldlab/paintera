package org.janelia.saalfeldlab.util;

import gnu.trove.set.hash.TLongHashSet;
import org.junit.Assert;
import org.junit.Test;

public class SetsTest {

	@Test
	public void testContainedInFirstButNotInSecond()
	{


		final TLongHashSet set1 = new TLongHashSet(new long[] {1, 2});
		final TLongHashSet set2 = new TLongHashSet(new long[] {2, 3});
		final TLongHashSet set3 = new TLongHashSet(new long[] {4, 5});

		Assert.assertEquals(new TLongHashSet(), Sets.containedInFirstButNotInSecond(set1, set1));
		Assert.assertEquals(new TLongHashSet(), Sets.containedInFirstButNotInSecond(set2, set2));
		Assert.assertEquals(new TLongHashSet(), Sets.containedInFirstButNotInSecond(set3, set3));

		Assert.assertEquals(new TLongHashSet(new long[] {1}), Sets.containedInFirstButNotInSecond(set1, set2));
		Assert.assertEquals(new TLongHashSet(new long[] {3}), Sets.containedInFirstButNotInSecond(set2, set1));

		Assert.assertEquals(set1, Sets.containedInFirstButNotInSecond(set1, set3));
		Assert.assertEquals(set2, Sets.containedInFirstButNotInSecond(set2, set3));
		Assert.assertEquals(set3, Sets.containedInFirstButNotInSecond(set3, set1));
		Assert.assertEquals(set3, Sets.containedInFirstButNotInSecond(set3, set2));

	}

}
