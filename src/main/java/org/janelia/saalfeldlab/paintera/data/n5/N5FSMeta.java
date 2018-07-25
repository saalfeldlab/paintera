package org.janelia.saalfeldlab.paintera.data.n5;

import java.io.IOException;

import com.google.gson.annotations.Expose;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;

public class N5FSMeta implements N5Meta
{

	@Expose
	private final String n5;

	@Expose
	private final String dataset;

	public N5FSMeta(final N5FSReader reader, final String dataset) throws ReflectionException
	{
		this(fromReader(reader), dataset);
	}

	public N5FSMeta(final String n5, final String dataset)
	{
		super();
		this.n5 = n5;
		this.dataset = dataset;
	}

	@Override
	public N5FSReader reader() throws IOException
	{
		return new N5FSReader(n5);
	}

	@Override
	public N5FSWriter writer() throws IOException
	{
		return new N5FSWriter(n5);
	}

	@Override
	public String dataset()
	{
		return dataset;
	}

	private static String fromReader(final N5FSReader reader) throws ReflectionException
	{

		try
		{
			return (String) ReflectionHelpers.searchForField(reader.getClass(), "basePath").get(reader);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
		{
			throw new ReflectionException(e);
		}

	}

	public String basePath()
	{
		return n5;
	}

}
