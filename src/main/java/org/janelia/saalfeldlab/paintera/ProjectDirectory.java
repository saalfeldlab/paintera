package org.janelia.saalfeldlab.paintera;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProjectDirectory implements Closeable {

	private File directory = null;

	private File actualDirectory = null;

	private LockFile lock = null;

	private boolean isClosed = false;

	private List<Consumer<ProjectDirectory>> listeners = new ArrayList<>();

	public void setReadOnlyDirectory(final File readOnlyDir) throws IOException {
		if (readOnlyDir == null || readOnlyDir.canWrite()) {
			throw new IllegalArgumentException("Directory is writable; Cannot load project as read-only (" + readOnlyDir + ")");
		}
		lock = null;
		directory = readOnlyDir;
		actualDirectory = inferActualDirectory(directory);
	}

	public void setDirectory(
			final File directory,
			final Function<LockFile.UnableToCreateLock, Boolean> askIgnoreLock) throws LockFile.UnableToCreateLock, IOException {

		if (this.isClosed)
			return;
		if (this.directory == null && directory == null && this.actualDirectory != null)
			// || directory != null && directory.equals(this.directory)) TODO should we ignore directory == this.directory?
			return;

		if (directory != null && directory.exists() && !directory.canWrite()) {
			setReadOnlyDirectory(directory);
			return;
		}
		final File newActualDirectory = inferActualDirectory(directory);
		newActualDirectory.mkdirs();
		final LockFile newLock = new LockFile(new File(newActualDirectory, ".paintera"), "lock");
		try {
			newLock.lock();
		} catch (final LockFile.UnableToCreateLock e) {
			final boolean ignoreLock = askIgnoreLock.apply(e);
			if (ignoreLock) {
				newLock.remove();
				newLock.lock();
			} else
				throw e;
		}

		if (this.lock != null && !Files.isSameFile(this.lock.getLockFile().toPath(), newLock.getLockFile().toPath()))
			this.lock.removeIfLocked();
		this.directory = directory;
		this.actualDirectory = newActualDirectory;
		this.lock = newLock;
		stateChanged();
	}

	public File getDirectory() {

		return this.directory;
	}

	public File getActualDirectory() {

		return this.actualDirectory;
	}

	public boolean isClosed() {

		return this.isClosed;
	}

	public void addListener(final Consumer<ProjectDirectory> listener) {

		this.listeners.add(listener);
		listener.accept(this);
	}

	@Override
	public void close() {

		this.isClosed = true;
		if (this.lock != null)
			this.lock.removeIfLocked();
		this.lock = null;
		this.directory = null;
		this.actualDirectory = null;
		this.stateChanged();
	}

	private File inferActualDirectory(final File directory) throws IOException {

		return directory == null
				? temporaryN5PainteraProjectDirectory()
				: directory;
	}

	private File temporaryN5PainteraProjectDirectory() throws IOException {

		return new File(Files.createTempDirectory("paintera-project-").toString() + ".n5");
	}

	private void stateChanged() {

		this.listeners.forEach(l -> l.accept(this));
	}

}
