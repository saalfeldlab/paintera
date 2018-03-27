package bdv.bigcat.viewer.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.atlas.source.AtlasSourceState;
import bdv.bigcat.viewer.meshes.MeshGenerator.ShapeKey;
import net.imglib2.Interval;
import net.imglib2.cache.Cache;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;

public abstract class MeshExporter
{
	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	protected int numberOfFaces = 0;

	public void exportMesh( final AtlasSourceState< ?, ? >[] state, final long[] ids, final int scale, String[] paths )
	{
		assert ids.length == paths.length;
		for ( int i = 0; i < ids.length; i++ )
		{
			numberOfFaces = 0;
			exportMesh( state[ i ], ids[ i ], scale, paths[ i ] );
		}
	}

	public void exportMesh( final AtlasSourceState< ?, ? > state, final long id, final int scaleIndex, String path )
	{
		final Cache< Long, Interval[] >[] blockListCache = state.blocklistCacheProperty().get();
		final Cache< ShapeKey, Pair< float[], float[] > >[] meshCache = state.meshesCacheProperty().get();
		// all blocks from id
		Interval[] blocks = null;
		try
		{
			blocks = blockListCache[ scaleIndex ].get( id );
		}
		catch ( ExecutionException e )
		{
			LOG.warn( "Could not get mesh block list for id {}: {}", id,
					e.getMessage() );
		}

		// generate keys from blocks, scaleIndex, and id
		final List< ShapeKey > keys = new ArrayList<>();
		for ( final Interval block : blocks )
			// ignoring simplification iterations parameter
			keys.add( new ShapeKey( id, scaleIndex, 0, Intervals.minAsLongArray( block ), Intervals.maxAsLongArray( block ) ) );

		for ( final ShapeKey key : keys )
		{
			Pair< float[], float[] > verticesAndNormals;
			try
			{
				verticesAndNormals = meshCache[ scaleIndex ].get( key );
				assert verticesAndNormals.getA().length == verticesAndNormals.getB().length: "Vertices and normals must have the same size.";
				save( path, id, verticesAndNormals.getA(), verticesAndNormals.getB(), BooleanUtils.toBoolean( numberOfFaces ) );
				numberOfFaces += verticesAndNormals.getA().length / 3;
			}
			catch ( final ExecutionException e )
			{
				LOG.warn( "Was not able to retrieve mesh for {}: {}", key, e.getMessage() );
			}
			catch ( final RuntimeException e )
			{
				LOG.warn( "{} : {}", e.getClass(), e.getMessage() );
				e.printStackTrace();
				throw e;
			}
		}

	}

	protected abstract void save( String path, long id, float[] vertices, float[] normals, boolean append );

}
