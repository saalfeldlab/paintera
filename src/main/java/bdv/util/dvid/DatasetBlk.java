package bdv.util.dvid;

import java.io.IOException;
import java.net.MalformedURLException;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;

public abstract class DatasetBlk extends Dataset
{

	public DatasetBlk( Node node, String name )
	{
		super( node, name );
	}
	
	public abstract < T extends RealType< T > > void get( 
			RandomAccessibleInterval< T > target,
			int[] offset
			) throws MalformedURLException, IOException;
	
	public abstract < T extends RealType< T > > void put( 
			RandomAccessibleInterval< T > source,
			int[] offset
			) throws MalformedURLException, IOException;
			

}
