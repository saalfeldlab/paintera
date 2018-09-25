package org.janelia.saalfeldlab.util.n5;

import org.apache.commons.io.FileUtils;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.util.MakeUnchecked;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class N5TestUtil {

	static N5Writer fileSystemWriterAtTmpDir() throws IOException {
		final Path tmp = Files.createTempDirectory(null);
		final File dir = tmp.toFile();
		dir.deleteOnExit();
		Runtime.getRuntime().addShutdownHook(new Thread(MakeUnchecked.runnable(() -> FileUtils.deleteDirectory(dir))));
		final N5FSWriter writer = new N5FSWriter(tmp.toAbsolutePath().toString());
		return writer;
	}

	static DatasetAttributes defaultAttributes()
	{
		return defaultAttributes(DataType.UINT8);
	}

	static DatasetAttributes defaultAttributes(DataType t)
	{
		return new DatasetAttributes(new long[] {1}, new int[] {1}, t, new RawCompression());
	}
}
