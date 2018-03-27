package bdv.bigcat.viewer.meshes;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeshExporterBinary extends MeshExporter
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Override
	protected void save( String path, long id, float[] vertices, float[] normals, boolean append )
	{
		save( path + ".vertices", vertices, append );
		save( path + ".normals", normals, append );
	}

	private void save( String path, float[] info, boolean append )
	{
		try
		{
			DataOutputStream stream = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( path, append ) ) );
			try
			{
				for ( int i = 0; i < info.length; i++ )
				{
					stream.writeFloat( info[ i ] );
				}
				stream.close();
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
