package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.io.IOException;

public class LockFile
{

	public static class UnableToCreateLock extends Exception
	{

		private final File lockFile;

		public UnableToCreateLock(final File lockFile, final Throwable cause)
		{
			super(cause);
			this.lockFile = lockFile;
		}

		public UnableToCreateLock(final File lockFile, final String message)
		{
			super(message);
			this.lockFile = lockFile;
		}

		public File getLockFile()
		{
			return this.lockFile;
		}
	}

	public static class LockAlreadyExists extends UnableToCreateLock
	{

		public LockAlreadyExists(final File lockFile)
		{
			super(lockFile, "Lock already exists: " + lockFile);
		}
	}

	private final File file;

	private boolean isLocked = false;

	public LockFile(final String directory, final String filename)
	{
		this(new File(directory), filename);
	}

	public LockFile(final File directory, final String filename)
	{
		directory.mkdirs();
		this.file = new File(directory, filename);
		this.file.deleteOnExit();
	}

	public boolean lock() throws UnableToCreateLock
	{

		try
		{
			this.isLocked = this.file.createNewFile();
		} catch (final IOException e)
		{
			throw new UnableToCreateLock(this.file, e);
		}
		if (!this.isLocked) { throw new LockAlreadyExists(this.file); }
		return this.isLocked;
	}

	public void remove()
	{
		if (isLocked)
		{
			this.file.delete();
			isLocked = false;
		}
	}

	public boolean isLocked()
	{
		return this.isLocked;
	}

	public File getLockFile()
	{
		return this.file;
	}

}
