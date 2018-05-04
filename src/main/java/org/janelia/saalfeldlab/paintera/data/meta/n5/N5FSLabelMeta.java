package org.janelia.saalfeldlab.paintera.data.meta.n5;

import org.janelia.saalfeldlab.n5.N5FSReader;

public class N5FSLabelMeta extends N5FSMeta implements N5LabelMeta
{

	public N5FSLabelMeta( N5FSReader n5, String dataset ) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
	{
		super( n5, dataset );
	}

}
