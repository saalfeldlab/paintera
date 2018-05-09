package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Arrays;

import org.janelia.saalfeldlab.paintera.data.meta.Meta;

public class UndefinedDependency extends Exception
{

	public UndefinedDependency( final Meta meta, final Meta dependency, final Meta[] availableMetas )
	{
		super( String.format(
				"Dependency %s not defined for %s. Available dependencies: %s.",
				dependency,
				meta,
				Arrays.toString( availableMetas ) ) );
	}

}
