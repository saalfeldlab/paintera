package org.janelia.saalfeldlab.bdv.util;

import org.janelia.saalfeldlab.n5.KeyValueAccess;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.StorageFormat;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class N5IdServiceTest extends IdServiceTest<N5IdService> {

	private static List<N5Writer> writers = new ArrayList<>();

	private static Supplier<N5Writer> tempWriterFactory;
	static {
		new N5Factory() {

			@Override public N5Writer openWriter(@Nullable StorageFormat storage, @Nullable KeyValueAccess access, URI location) {


				return super.openWriter(storage, access, location);
			}
		};
		tempWriterFactory = () -> {
			final Path path;
			try {
				path = Files.createTempDirectory("N5IdServiceTest");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			final N5Writer writer = new N5Factory().openWriter(StorageFormat.N5, path.toUri());
			writers.add(writer);
			return writer;
		};
	}

	@AfterAll
	public static void tearDown() {
		writers.forEach(N5Writer::remove);
		writers.clear();
	}

	@Override N5IdService newIdService(int currentId) {

		final N5Writer n5 = tempWriterFactory.get();
		n5.createGroup("dataset");
		return new N5IdService(n5, "dataset", currentId);
	}
}
