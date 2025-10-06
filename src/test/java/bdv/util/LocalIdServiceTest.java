package bdv.util;

import org.janelia.saalfeldlab.paintera.id.LocalIdService;

public class LocalIdServiceTest extends IdServiceTest<LocalIdService> {

	@Override LocalIdService newIdService(int currentId) {

		return new LocalIdService(currentId);
	}
}
