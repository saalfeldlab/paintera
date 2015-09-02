package bdv.bigcat;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import javax.naming.spi.DirectoryManager;
import javax.xml.ws.http.HTTPException;

import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.dvid.Labels64DataInstance;
import bdv.img.dvid.Labels64DataInstance.Extended;
import bdv.labels.labelset.DvidLabels64MultisetSetupImageLoader;
import bdv.util.JsonHelper;
import bdv.util.dvid.DatasetKeyValue;
import bdv.util.dvid.Repository;
import bdv.util.dvid.Server;

public class Downscale
{
	
	public static void main( String[] args ) throws MalformedURLException, IOException
	{
		
		final String url = "http://vm570.int.janelia.org:8080";
		final String labelsBase = "multisets-labels-downscaled";
		String uuid = "4668221206e047648f622dc4690ff7dc";
		
		Server server = new Server( url );
		Repository repo = new Repository( server, uuid );
		
		String baseUrl = "http://emrecon100.janelia.priv";
		String baseApiUrl = baseUrl + "/api";
		String baseUuid = "2a3fd320aef011e4b0ce18037320227c";
		String baseName = "bodies";
		
		Extended info = ( (Labels64DataInstance)JsonHelper.fetch(
				baseApiUrl + "/node/" + baseUuid + "/" + baseName + "/info",
				Labels64DataInstance.class ) ).Extended;
		
		long[] dimensions = new long[] {
				info.MaxPoint[ 0 ] + 1 - info.MinPoint[ 0 ],
				info.MaxPoint[ 1 ] + 1 - info.MinPoint[ 1 ],
				info.MaxPoint[ 2 ] + 1 - info.MinPoint[ 2 ]
		};
		
		DatasetKeyValue[] stores = new DatasetKeyValue[ 4 ];
		double[][] resolutions = new double[stores.length + 1][];
		
		int[] blockSizes = new int[] { 32, 32 ,32 };
		
		for ( int i = 0; i < resolutions.length; ++i )
		{
			int scale = 1 << i;
			resolutions[ i ] = new double[] { scale, scale, scale };
			if ( i == 0 ) continue;
			stores[ i - 1 ] = new DatasetKeyValue( repo.getRootNode(), labelsBase + "-" + scale );
			try {
				repo.getRootNode().createDataset( stores[ i - 1 ].getName(), DatasetKeyValue.TYPE );
			}
			catch ( HTTPException e )
			{
//				e.printStackTrace();
			}
		}
		
		int nLevels = stores.length + 1;
		
		try
		{
			final DvidLabels64MultisetSetupImageLoader dvidLabelsMultisetImageLoader = 
					new DvidLabels64MultisetSetupImageLoader(
					1,
					baseApiUrl,
					baseUuid,
					baseName,
					resolutions,
					stores );
			
			VolatileGlobalCellCache cache = new VolatileGlobalCellCache( 1, 1, nLevels, 10 );
			
			dvidLabelsMultisetImageLoader.setCache( cache );
			
			for ( int level = 1; level < nLevels; ++ level )
			{
				double[] currRes = resolutions[ level ];
				
				long[] currDim = new long[] {
						( long ) ( dimensions[ 0 ] / currRes[ 0 ] ),
						( long ) ( dimensions[ 0 ] / currRes[ 0 ] ),
						( long ) ( dimensions[ 0 ] / currRes[ 0 ] )
				};
				
				long[] currPos = new long[ 3 ];
				
				System.out.println( Arrays.toString(  currDim  ) + " " + Arrays.toString(  dimensions  ) );
				
				for ( int x = 0; x < currDim[ 0 ]; x += blockSizes[ 0 ] )
				{
					currPos[ 0 ] = x;
					for ( int y = 0; y < currDim[ 1 ]; y += blockSizes[ 1 ] )
					{
						currPos[ 1 ] = y;
						for ( int z = 0; z < currDim[ 2 ]; z += blockSizes[ 2 ] )
						{
							currPos[ 2 ] = z;
							dvidLabelsMultisetImageLoader.downscaleLoader.loadArray( 
									0, 
									1, 
									level, 
									blockSizes, 
									currPos
									);
						}
					}
				}
				
			}
			
		}
		catch ( Exception ex )
		{
			ex.printStackTrace( System.err );
		}
	}
}
