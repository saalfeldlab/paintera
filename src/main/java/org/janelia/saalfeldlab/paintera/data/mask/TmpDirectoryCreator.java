package org.janelia.saalfeldlab.paintera.data.mask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Optional;
import java.util.function.Supplier;

public class TmpDirectoryCreator implements Supplier<String> {

	private static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Path baseTempDir;

	private final String prefix;

	private final FileAttribute<?>[] attrs;

	public TmpDirectoryCreator(final Path baseDir, final String prefix, final FileAttribute<?>... attrs) {

		super();
		LOG.debug("Creating {} with dir={} prefix={} attrs={}", this.getClass().getSimpleName(), baseDir, prefix, attrs);
		this.baseTempDir = baseDir;
		this.prefix = prefix;
		this.attrs = attrs;
	}

	@Override
	public String get() {

		try {
			Optional.ofNullable(baseTempDir).map(Path::toFile).ifPresent(File::mkdirs);
			final Path tmpDir;
			if (baseTempDir == null)
				tmpDir = Files.createTempDirectory(prefix, attrs);
			else
				tmpDir = Files.createTempDirectory(baseTempDir, prefix, attrs);

			tmpDir.toFile().deleteOnExit(); //TODO meta ensure this is safe to do. It should be, if they are temporary...
			LOG.debug("Created tmp dir {}", tmpDir);
			return tmpDir.toString();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
