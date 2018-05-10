package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.N5FSReader;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class N5FSRawMeta< D extends NativeType< D >, T extends Volatile< D > & NativeType< T > & RealType< T > > extends N5FSMeta< D > implements N5RawMeta< D, T >
{

	public N5FSRawMeta( String root, String dataset )
	{
		super( root, dataset );
	}

	public N5FSRawMeta( N5FSReader n5, String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}
