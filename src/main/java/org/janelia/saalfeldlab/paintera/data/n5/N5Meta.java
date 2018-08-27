package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface N5Meta
{

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	N5Reader reader() throws IOException;

	N5Writer writer() throws IOException;

	String dataset();

	default DatasetAttributes datasetAttributes() throws IOException
	{
		return reader().getDatasetAttributes(dataset());
	}

	static N5Meta fromReader(final N5Reader reader, final String dataset) throws ReflectionException
	{
		if (reader instanceof N5FSReader) { return new N5FSMeta((N5FSReader) reader, dataset); }

		if (reader instanceof N5HDF5Reader) { return new N5HDF5Meta((N5HDF5Reader) reader, dataset); }

		LOG.warn("Cannot create meta for reader of type {}", reader.getClass().getName());

		return null;
	}

}
