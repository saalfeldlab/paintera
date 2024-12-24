package org.janelia.saalfeldlab.util;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashPriorityQueueTest {

	@Test
	public void test() {

		final HashPriorityQueue<Integer, String> pq = new HashPriorityQueue<>(Comparator.reverseOrder());
		pq.addOrUpdate(2, "q");
		pq.addOrUpdate(5, "abc");
		pq.addOrUpdate(-1, "xyz");
		pq.addOrUpdate(10, "123");
		pq.addOrUpdate(7, "r");

		final HashPriorityQueue<Integer, String> pqOther = new HashPriorityQueue<>(pq);

		assertEquals(5, pq.size());
		assertArrayEquals(
				new String[]{"123", "r", "abc", "q", "xyz"},
				pq.poll(5).toArray(new String[0])
		);
		assertTrue(pq.isEmpty());

		pqOther.addOrUpdate(12, "q");
		pqOther.addOrUpdate(5, "www");
		pqOther.addOrUpdate(7, "xyz");

		assertEquals(6, pqOther.size());
		assertTrue(pq.isEmpty());

		assertEquals("q", pqOther.peek());
		assertEquals("q", pqOther.peek());
		assertEquals(6, pqOther.size());

		assertEquals("q", pqOther.poll());
		assertEquals("123", pqOther.poll());
		assertArrayEquals(
				new String[]{"r", "xyz"},
				new TreeSet<>(pqOther.poll(2)).toArray(new String[0]) // sort next two entries which should have equal priority
		);
		assertArrayEquals(
				new String[]{"abc", "www"},
				new TreeSet<>(pqOther.poll(2)).toArray(new String[0]) // sort next two entries which should have equal priority
		);
		assertTrue(pqOther.isEmpty());
	}
}
