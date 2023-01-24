package org.janelia.saalfeldlab.paintera.serialization;

import gnu.trove.set.hash.TIntHashSet;

import java.util.Arrays;

public class HasCyclicDependencies extends Exception {

	/**
	 *
	 */
	private static final long serialVersionUID = 5763363058057863118L;

	public HasCyclicDependencies(final TIntHashSet[] nodeEdgeMap) {

		super(String.format("Cyclic dependencies: %s", Arrays.toString(nodeEdgeMap)));
	}

}
