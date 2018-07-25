package org.janelia.saalfeldlab.paintera.state;

import java.util.Collection;

public class HasDependents extends Exception
{

	/**
	 *
	 */
	private static final long serialVersionUID = 4008974783523807555L;

	public HasDependents(final SourceState<?, ?> state, final Collection<? extends SourceState<?, ?>> dependents)
	{
		super(String.format("State %s still has dependents: %s", state, dependents));
	}

}
