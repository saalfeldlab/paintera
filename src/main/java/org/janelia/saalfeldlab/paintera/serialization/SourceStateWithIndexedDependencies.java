package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;

import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.Expose;

public class SourceStateWithIndexedDependencies< D, T >
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Expose
	private final SourceState< D, T > state;

	@Expose
	private final int[] dependencies;

	public SourceStateWithIndexedDependencies( final SourceState< D, T > state, final SourceInfo sourceInfo )
	{
		this( state, toIndices( state, sourceInfo ) );
	}

	public SourceStateWithIndexedDependencies( final SourceState< D, T > state, final int[] dependencies )
	{
		super();
		this.state = state;
		this.dependencies = dependencies;
		LOG.warn( "Created {} with {} and {}", this.getClass().getName(), state, dependencies );
	}

	public int[] dependsOn()
	{
		return this.dependencies.clone();
	}

	public SourceState< D, T > state()
	{
		return this.state;
	}

	private static < D, T > int[] toIndices( final SourceState< D, T > state, final SourceInfo sourceInfo )
	{
		final SourceState< ?, ? >[] dependsOn = state.dependsOn();
		final int[] indices = new int[ dependsOn.length ];
		for ( int i = 0; i < dependsOn.length; ++i )
		{
			indices[ i ] = sourceInfo.indexOf( dependsOn[ i ].getDataSource() );
		}
		return indices;
	}

}
