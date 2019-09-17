package org.janelia.saalfeldlab.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Comparator;
import java.util.TreeSet;

public class HashPriorityQueueTest
{
	@Test
	public void test()
	{
		final HashPriorityQueue<Integer, String> pq = new HashPriorityQueue<>(Comparator.reverseOrder());
		pq.addOrUpdate(2, "q");
		pq.addOrUpdate(5, "abc");
		pq.addOrUpdate(-1, "xyz");
		pq.addOrUpdate(10, "123");
		pq.addOrUpdate(7, "r");

		final HashPriorityQueue<Integer, String> pqOther = new HashPriorityQueue<>(pq);

		Assert.assertEquals(5, pq.size());
		Assert.assertArrayEquals(
				new String[] {"123", "r", "abc", "q", "xyz"},
				pq.poll(5).toArray(new String[0])
			);
		Assert.assertTrue(pq.isEmpty());

		pqOther.addOrUpdate(12, "q");
		pqOther.addOrUpdate(5, "www");
		pqOther.addOrUpdate(7, "xyz");

		Assert.assertEquals(6, pqOther.size());
		Assert.assertTrue(pq.isEmpty());

		Assert.assertEquals("q", pqOther.peek());
		Assert.assertEquals("q", pqOther.peek());
		Assert.assertEquals(6, pqOther.size());

		Assert.assertEquals("q", pqOther.poll());
		Assert.assertEquals("123", pqOther.poll());
		Assert.assertEquals(
				new String[] {"r", "xyz"},
				new TreeSet<>(pqOther.poll(2)).toArray(new String[0]) // sort next two entries which should have equal priority
			);
		Assert.assertEquals(
				new String[] {"abc", "www"},
				new TreeSet<>(pqOther.poll(2)).toArray(new String[0]) // sort next two entries which should have equal priority
		);
		Assert.assertTrue(pqOther.isEmpty());
	}
}
