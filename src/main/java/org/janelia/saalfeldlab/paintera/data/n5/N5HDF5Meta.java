package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;

import com.google.gson.annotations.Expose;

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

	public N5HDF5Meta( final String file, final String dataset, final int[] defaultCellDimensions, final boolean overrideCellDimensions )
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
		return new N5HDF5Reader( file );
	}

	@Override
	public N5HDF5Writer writer() throws IOException
	{
		return new N5HDF5Writer( file );
	}

	@Override
	public String dataset()
	{
		return dataset;
	}

}
