package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Arrays;

public class UndefinedDependency extends Exception
{

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public UndefinedDependency(int[] dependencies, int numberOfSources)
	{
		super(String.format(
				"Dependency %s out of range [0,%d]",
				Arrays.toString(dependencies),
				numberOfSources - 1
		                   ));
	}

}
