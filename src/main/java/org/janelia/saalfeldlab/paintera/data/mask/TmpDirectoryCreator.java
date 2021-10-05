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

  private final Supplier<Path> dir;

  private final String prefix;

  private final FileAttribute<?>[] attrs;

  public TmpDirectoryCreator(final Supplier<Path> dir, final String prefix, final FileAttribute<?>... attrs) {

	super();
	LOG.debug("Creating {} with dir={} prefix={} attrs={}", this.getClass().getSimpleName(), dir, prefix, attrs);
	this.dir = dir;
	this.prefix = prefix;
	this.attrs = attrs;
  }

  @Override
  public String get() {

	final Path dir = this.dir.get();
	try {
	  Optional.ofNullable(dir).map(Path::toFile).ifPresent(File::mkdirs);
	  final Path tmpDir = dir == null
			  ? Files.createTempDirectory(prefix, attrs)
			  : Files.createTempDirectory(dir, prefix, attrs);
	  tmpDir.toFile().deleteOnExit(); //TODO meta ensure this is safe to do. It should be, if they are temporary...
	  LOG.debug("Created tmp dir {}", tmpDir.toString());
	  return tmpDir.toString();
	} catch (final IOException e) {
	  throw new RuntimeException(e);
	}
  }

}
