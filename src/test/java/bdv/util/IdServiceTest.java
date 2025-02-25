package bdv.util;

import org.janelia.saalfeldlab.paintera.id.IdService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdServiceTest {

	final long[] ids1 = new long[]{1, 2, 3};
	final long[] ids2 = new long[]{-1, 2, 3};
	final long[] ids3 = new long[]{1, -2, -3};

	@Test
	public void testMax() {

		assertEquals(3, IdService.max(ids1));
		assertEquals(-1, IdService.max(ids2));
		assertEquals(-2, IdService.max(ids3));
	}

}
