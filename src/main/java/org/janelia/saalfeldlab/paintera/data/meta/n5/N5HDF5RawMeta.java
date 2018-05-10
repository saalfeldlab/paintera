package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class N5HDF5RawMeta< D extends NativeType< D >, T extends Volatile< D > & NativeType< T > & RealType< T > > extends N5HDF5Meta< D > implements N5RawMeta< D, T >
{
	public N5HDF5RawMeta(
			final N5HDF5Reader n5,
			final String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}
