package bdv.bigcat.viewer.meshes;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshExporterBinary extends MeshExporter
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Override
	protected void save( String path, long id, float[] vertices, float[] normals, boolean append )
	{
		try
		{
			OutputStream outputStream = new FileOutputStream( path, append );

			final StringBuilder sb = new StringBuilder();
			for ( int i = 0; i < vertices.length; i += 3 )
			{
				sb.append( "\nv " ).append( vertices[ i ] ).append( " " ).append( vertices[ i + 1 ] ).append( " " ).append( vertices[ i + 2 ] );
			}

			sb.append( "\n" );
			for ( int i = 0; i < normals.length; i += 3 )
			{
				sb.append( "\nvn " ).append( normals[ i ] ).append( " " ).append( normals[ i + 1 ] ).append( " " ).append( normals[ i + 2 ] );
			}

			try
			{
				outputStream.write( sb.toString().getBytes() );
				outputStream.close();
			}
			catch ( IOException e )
			{
				LOG.warn( "Couldn't write data to the file {}: {}", path, e.getMessage() );
			}

		}
		catch ( FileNotFoundException e )
		{
			LOG.warn( "Couldn't find file {}: {}", path, e.getMessage() );
		}
	}

}
