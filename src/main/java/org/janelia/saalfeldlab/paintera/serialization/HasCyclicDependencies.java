package org.janelia.saalfeldlab.paintera.serialization;

import java.util.Arrays;

import org.janelia.saalfeldlab.paintera.data.meta.Meta;

public class HasCyclicDependencies extends Exception
{

	public HasCyclicDependencies( final Meta[] metas )
	{
		super( String.format( "Cyclic dependencies: %s", makeString( metas ) ) );
	}

	private static String makeString( final Meta[] metas )
	{
		final StringBuilder sb = new StringBuilder( "{" );

		if ( metas.length > 0 )
		{
			sb
					.append( metas[ 0 ] )
					.append( ":" )
					.append( Arrays.toString( metas[ 0 ].dependsOn() ) );
		}

		for ( int i = 1; i < metas.length; ++i )
		{
			final Meta meta = metas[ i ];
			sb
					.append( ", " )
					.append( meta )
					.append( ":" )
					.append( Arrays.toString( meta.dependsOn() ) );
		}

		sb.append( "}" );

		return sb.toString();
	}

}
