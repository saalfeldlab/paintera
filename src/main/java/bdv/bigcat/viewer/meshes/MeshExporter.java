package bdv.bigcat.viewer.meshes;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.ArrayUtils;
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

	public void exportMeshes( final AtlasSourceState< ?, ? > state, final long[] ids, final int[] scales, final int[] simplificationIterations, String[] paths )
	{
		assert ids.length == scales.length;
		for ( int i = 0; i < ids.length; i++ )
		{
			exportMesh( state, ids[ i ], scales[ i ], paths[ i ] );
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

		float[] allVertices = null;
		float[] allNormals = null;
		for ( final ShapeKey key : keys )
		{
			Pair< float[], float[] > verticesAndNormals;
			try
			{
				verticesAndNormals = meshCache[ scaleIndex ].get( key );
				allVertices = ArrayUtils.addAll( allVertices, verticesAndNormals.getA() );
				allNormals = ArrayUtils.addAll( allNormals, verticesAndNormals.getB() );
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

		assert allVertices.length == allNormals.length: "Vertices and normals must have the same size.";
		save( path, id, allVertices, allNormals );
	}

	protected abstract void save( String path, long id, float[] vertices, float[] normals );

}
