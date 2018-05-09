package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;

import ch.systemsx.cisd.hdf5.IHDF5Reader;

public class N5HDF5Meta implements N5Meta
{

	private static final String READER_FIELD_NAME = "reader";

	private static final String DEFAULT_BLOCK_SIZE_FIELD_NAME = "defaultBlockSize";

	private static final String OVERIDE_BLOCK_SIZE_FIELD_NAME = "overrideBlockSize";

	public final String h5file;

	public final String dataset;

	public final int[] defaultBlockSize;

	public final boolean overrideBlockSize;

	public N5HDF5Meta( final N5HDF5Reader n5, final String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		this( getBasePath( n5 ), dataset, getDefaultBlockSize( n5 ), getOverrideBlockSize( n5 ) );
	}

	public N5HDF5Meta( final String h5file, final String dataset, final int[] defaultBlockSize, final boolean overrideBlockSize )
	{
		super();
		this.h5file = h5file;
		this.dataset = dataset;
		this.defaultBlockSize = defaultBlockSize;
		this.overrideBlockSize = true;
	}

	public static String getBasePath( final N5HDF5Reader n5 ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		return ( ( IHDF5Reader ) ReflectionHelpers.searchForField( n5.getClass(), READER_FIELD_NAME ).get( n5 ) ).getFile().getAbsolutePath();
	}

	public static int[] getDefaultBlockSize( final N5HDF5Reader n5 ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		return ( int[] ) ReflectionHelpers.searchForField( n5.getClass(), DEFAULT_BLOCK_SIZE_FIELD_NAME ).get( n5 );
	}

	public static boolean getOverrideBlockSize( final N5HDF5Reader n5 ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		return ( boolean ) ReflectionHelpers.searchForField( n5.getClass(), OVERIDE_BLOCK_SIZE_FIELD_NAME ).get( n5 );
	}

	@Override
	public N5Reader reader() throws IOException
	{
		return new N5HDF5Reader( h5file, overrideBlockSize, defaultBlockSize );
	}

	@Override
	public N5Writer writer() throws IOException
	{
		return new N5HDF5Writer( h5file, defaultBlockSize );
	}

	@Override
	public String dataset()
	{
		return dataset;
	}

	@Override
	public String toString()
	{
		return String.format( "{%s: {file:%s, dataset:%s} }", getClass().getName(), h5file, dataset );
	}

	@Override
	public boolean equals( final Object other )
	{
		return other instanceof N5FSMeta && toString().equals( other.toString() );
	}

}
