package bdv.bigcat.viewer.meshes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshExporterObj extends MeshExporter
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Override
	protected void save( String path, long id, float[] vertices, float[] normals )
	{
		final File file = new File( path );
		try
		{
			FileWriter writer = new FileWriter( file );
			float[] texCoords = new float[] { 0.0f, 0.0f };
			final StringBuilder sb = new StringBuilder().append( "# id: " ).append( id ).append( "\n" );

			sb.append( "\n" );
			final int numVertices = vertices.length;
			for ( int k = 0; k < numVertices; k += 3 )
				sb.append( "\nv " ).append( vertices[ k + 0 ] ).append( " " ).append( vertices[ k + 1 ] ).append( " " ).append( vertices[ k + 2 ] );

			sb.append( "\n" );
			final int numNormals = normals.length;
			for ( int k = 0; k < numNormals; k += 3 )
				sb.append( "\nvn " ).append( normals[ k + 0 ] ).append( " " ).append( normals[ k + 1 ] ).append( " " ).append( normals[ k + 2 ] );

			sb.append( "\n" );
			final int numTexCoords = texCoords.length;
			for ( int k = 0; k < numTexCoords; k += 2 )
				sb.append( "\nvt " ).append( texCoords[ k + 0 ] ).append( " " ).append( texCoords[ k + 1 ] );

			sb.append( "\n" );
			for ( int k = 0; k < numVertices / 3; k += 3 )
			{
				sb
						.append( "\nf " ).append( k + 1 ).append( "/" ).append( 1 ).append( "/" ).append( k + 1 )
						.append( " " ).append( k + 2 ).append( "/" ).append( 1 ).append( "/" ).append( k + 2 )
						.append( " " ).append( k + 3 ).append( "/" ).append( 1 ).append( "/" ).append( k + 3 );
			}

			try
			{
				writer.write( sb.toString() );
				writer.close();
			}
			catch ( final IOException e )
			{
				LOG.warn( "Couldn't write data to the file {}: {}", file.toPath(), e.getMessage() );
			}

		}
		catch ( IOException e )
		{
			LOG.warn( "Couldn't open the file {}: {}", file.toPath(), e.getMessage() );
		}
	}
}
