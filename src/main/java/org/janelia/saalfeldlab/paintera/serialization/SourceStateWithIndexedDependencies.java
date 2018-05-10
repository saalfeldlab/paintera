package org.janelia.saalfeldlab.paintera.serialization;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;

import com.google.gson.annotations.Expose;

public class SourceStateWithIndexedDependencies< D, T >
{

	@Expose
	private final SourceState< D, T > state;

	@Expose
	private final int[] dependencies;

	public SourceStateWithIndexedDependencies( SourceState< D, T > state, SourceInfo sourceInfo )
	{
		this( state, toIndices( state, sourceInfo ) );
	}

	public SourceStateWithIndexedDependencies( SourceState< D, T > state, int[] dependencies )
	{
		super();
		this.state = state;
		this.dependencies = dependencies;
	}

	public int[] dependsOn()
	{
		return this.dependencies.clone();
	}

	public SourceState< D, T > state()
	{
		return this.state;
	}

	private static < D, T > int[] toIndices( SourceState< D, T > state, SourceInfo sourceInfo )
	{
		SourceState< ?, ? >[] dependsOn = state.dependsOn();
		int[] indices = new int[ dependsOn.length ];
		for ( int i = 0; i < dependsOn.length; ++i )
		{
			indices[ i ] = sourceInfo.indexOf( dependsOn[ i ].getDataSource() );
		}
		return indices;
	}

}
