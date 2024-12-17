package bdv.util;

import org.janelia.saalfeldlab.paintera.id.LocalIdService;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class LocalIdServiceTest {

	final static long[] ids = LongStream.range(10, 100).toArray();

	@Test
	public void testAll() {

		final LocalIdService service = new LocalIdService();
		service.setNext(10);

		final long id1 = service.next();
		final long id2 = service.next();
		final long id3 = service.next();

		final long[] ids4to50 = service.next(47);

		final long id51 = service.next();

		final long[] ids52to90 = service.next(39);

		final long[] generatedIds = new long[ids.length];
		generatedIds[0] = id1;
		generatedIds[1] = id2;
		generatedIds[2] = id3;
		System.arraycopy(ids4to50, 0, generatedIds, 3, 47);
		generatedIds[50] = id51;
		System.arraycopy(ids52to90, 0, generatedIds, 51, 39);

		assertArrayEquals(ids, generatedIds);
	}

}
