package org.janelia.saalfeldlab.paintera.n5;

import org.janelia.saalfeldlab.n5.N5FSReader;

public class N5FSMeta
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
	public String toString()
	{
		return "{root:" + root + " dataset:" + dataset + "}";
	}

}
