package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

public class N5HDF5LabelMeta extends N5HDF5Meta implements N5LabelMeta
{

	public N5HDF5LabelMeta(
			final N5HDF5Reader n5,
			final String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}
