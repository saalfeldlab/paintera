package org.janelia.saalfeldlab.paintera.data.meta.n5;

import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.paintera.N5Helpers;
import org.janelia.saalfeldlab.paintera.data.meta.Meta;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;

public interface N5Meta< T extends NativeType< T > > extends Meta
{

	public N5Reader reader() throws IOException;

	public N5Writer writer() throws IOException;

	public String dataset();

	public default RandomAccessibleInterval< T > open() throws IOException
	{
		return N5Utils.< T >open( reader(), dataset() );
	}

	public default boolean isMultiscale() throws IOException
	{
		return N5Helpers.isMultiScale( reader(), dataset() );
	}

	public default boolean isLabelMultisetType( final boolean isMultiscale ) throws IOException
	{
		return N5Helpers.isLabelMultisetType( reader(), dataset(), isMultiscale );
	}

}
