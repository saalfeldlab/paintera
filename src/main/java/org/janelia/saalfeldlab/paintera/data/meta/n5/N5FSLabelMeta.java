package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.N5FSReader;

import net.imglib2.Volatile;
import net.imglib2.type.NativeType;

public class N5FSLabelMeta< D extends NativeType< D >, T extends Volatile< D > & NativeType< T > > extends N5FSMeta< D > implements N5LabelMeta< D, T >
{

	public N5FSLabelMeta( N5FSReader n5, String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}
