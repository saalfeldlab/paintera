package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;

public class N5FSMeta implements N5Meta
{

	private static final String BASE_PATH_FIELD_NAME = "basePath";

	public final String root;

	public final String dataset;

	public N5FSMeta( final N5FSReader n5, final String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		this( getBasePath( n5 ), dataset );
	}

	public N5FSMeta( final String root, final String dataset )
	{
		super();
		this.root = root;
		this.dataset = dataset;
	}

	public static String getBasePath( final N5FSReader n5 ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
	{
		return ( String ) ReflectionHelpers.searchForField( n5.getClass(), BASE_PATH_FIELD_NAME ).get( n5 );
	}

	@Override
	public N5Reader reader() throws IOException
	{
		return new N5FSReader( root );
	}

	@Override
	public N5Writer writer() throws IOException
	{
		return new N5FSWriter( root );
	}

	@Override
	public String dataset()
	{
		return dataset;
	}

	@Override
	public String toString()
	{
		return String.format( "{%s: {root:%s, dataset:%s} }", getClass().getName(), root, dataset );
	}

	@Override
	public boolean equals( final Object other )
	{
		return other instanceof N5FSMeta && toString().equals( other.toString() );
	}

}
