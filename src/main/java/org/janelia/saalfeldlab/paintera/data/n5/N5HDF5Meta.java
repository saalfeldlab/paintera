package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;

import ch.systemsx.cisd.hdf5.IHDF5Reader;
import com.google.gson.annotations.Expose;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;

public class N5HDF5Meta implements N5Meta
{

	@Expose
	private final String file;

	@Expose
	private final String dataset;

	@Expose
	private final int[] defaultCellDimensions;

	@Expose
	private final boolean overrideCellDimensions;

	public N5HDF5Meta(final N5HDF5Reader reader, final String dataset) throws ReflectionException
	{
		this(
				ihdfReaderFromReader(reader).getFile().getAbsolutePath().toString(),
				dataset,
				defaultBlockSizeFromReader(reader),
				overrideBlockSizeFromReader(reader)
		    );
	}

	public N5HDF5Meta(final String file, final String dataset, final int[] defaultCellDimensions, final boolean
			overrideCellDimensions)
	{
		super();
		this.file = file;
		this.dataset = dataset;
		this.defaultCellDimensions = defaultCellDimensions;
		this.overrideCellDimensions = overrideCellDimensions;
	}

	@Override
	public N5HDF5Reader reader() throws IOException
	{
		return new N5HDF5Reader(file);
	}

	@Override
	public N5HDF5Writer writer() throws IOException
	{
		return new N5HDF5Writer(file);
	}

	@Override
	public String dataset()
	{
		return dataset;
	}

	private static IHDF5Reader ihdfReaderFromReader(final N5HDF5Reader reader) throws ReflectionException
	{

		try
		{
			return (IHDF5Reader) ReflectionHelpers.searchForField(reader.getClass(), "reader").get(reader);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
		{
			throw new ReflectionException(e);
		}
	}

	private static int[] defaultBlockSizeFromReader(final N5HDF5Reader reader) throws ReflectionException
	{

		try
		{
			return (int[]) ReflectionHelpers.searchForField(reader.getClass(), "defaultBlockSize").get(reader);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
		{
			throw new ReflectionException(e);
		}
	}

	private static boolean overrideBlockSizeFromReader(final N5HDF5Reader reader) throws ReflectionException
	{

		try
		{
			return (boolean) ReflectionHelpers.searchForField(reader.getClass(), "overrideBlockSize").get(reader);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
		{
			throw new ReflectionException(e);
		}
	}

}
