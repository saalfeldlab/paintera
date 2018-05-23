package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

public interface N5Meta
{

	public N5Reader reader() throws IOException;

	public N5Writer writer() throws IOException;

	public String dataset();

	public static N5Meta fromReader( final N5Reader reader, final String dataset ) throws ReflectionException
	{
		if ( reader instanceof N5FSReader )
		{
			return new N5FSMeta( ( N5FSReader )reader, dataset );
		}

		if ( reader instanceof N5HDF5Reader )
		{
			return new N5HDF5Meta( ( N5HDF5Reader )reader, dataset );
		}

		return null;
	}

}
