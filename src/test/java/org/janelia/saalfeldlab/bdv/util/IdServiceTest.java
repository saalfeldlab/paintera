package org.janelia.saalfeldlab.bdv.util;

import org.janelia.saalfeldlab.paintera.id.IdService;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class IdServiceTest<T extends IdService> {

	final long[] ids1 = new long[]{1, 2, 3};
	final long[] ids2 = new long[]{-1, 2, 3};
	final long[] ids3 = new long[]{1, -2, -3};

	@Test
	public void testMax() {

		assertEquals(3, IdService.max(ids1));
		assertEquals(-1, IdService.max(ids2));
		assertEquals(-2, IdService.max(ids3));
	}

	abstract T newIdService(int currentId);

	@Test
	public void testAll() {

		final IdService service = newIdService(10);
		assertEquals(10, service.current());
		assertEquals(10, service.current());

		assertEquals(11, service.next());
		assertEquals(11, service.current());
		assertEquals(11, service.current());

		assertEquals(12, service.next());
		assertEquals(12, service.current());
		assertEquals(12, service.current());

		assertEquals(13, service.next());
		assertEquals(13, service.current());
		assertEquals(13, service.current());

		final long[] expected47Ids = LongStream.range(service.current() + 1, service.current() + 1 + 47).toArray();
		final long[] actual47Ids = service.next(47);
		assertEquals(47, actual47Ids.length);
		assertArrayEquals(expected47Ids, actual47Ids);
		assertEquals(expected47Ids[46], service.current());

		assertEquals(expected47Ids[46] + 1, service.next());
		assertEquals(expected47Ids[46] + 1, service.current());

		final long[] expectedNext39Ids = LongStream.range(service.current() + 1, service.current() + 1 + 39).toArray();
		final long[] actualNext39Ids = service.next(39);
		assertEquals(39, actualNext39Ids.length);
		assertArrayEquals(expectedNext39Ids, actualNext39Ids);
		assertEquals(expectedNext39Ids[38], service.current());

		assertEquals(100, service.current());
	}



}
