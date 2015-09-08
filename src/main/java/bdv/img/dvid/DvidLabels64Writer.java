package bdv.img.dvid;

import java.io.IOException;
import java.util.Random;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.dvid.DatasetBlk;
import bdv.util.dvid.DatasetBlkLabel;
import bdv.util.dvid.Repository;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.view.Views;

/**
 * 
 * labels64 is currently not avalaible in dvid. Once it is available again,
 * rewrite this to use DatasetLabels64 (not present at the moment).
 * 
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 *         Write any {@link RandomAccessibleInterval} of type {@link UnsignedLongType}
 *         into an existing dvid repository/dataset.
 *         
 */
public class DvidLabels64Writer extends AbstractDvidImageWriter< UnsignedLongType >
{

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk.
	 *
	 *            This calls
	 *            {@link DvidLabels64Writer#DvidLabels64ByteWriter(String, String, String, int)}
	 *            with a default block size of 32.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 **/
	public DvidLabels64Writer( final String url, final String uuid, final String dataSet )
			throws JsonSyntaxException, JsonIOException, IOException
	{
		this( url, uuid, dataSet, DatasetBlk.defaultBlockSize() );
	}

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type labelblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidLabels64Writer( final String url, final String uuid, final String dataSet, final int[] blockSize )
			throws JsonSyntaxException, JsonIOException, IOException
	{
		this( new DatasetBlkLabel( new Repository( url, uuid ).getRootNode(), dataSet ), blockSize );
	}
	
	public DvidLabels64Writer( DatasetBlkLabel dataset, int[] blockSize )
	{
		super( dataset, blockSize );
	}
	
	public DvidLabels64Writer( DatasetBlkLabel dataset ) throws JsonSyntaxException, JsonIOException, IOException
	{
		this( dataset, dataset.getBlockSize() );
	}

	/**
	 * This is for checking functionality. Adjust apiUrl, uuid and dataSet
	 * according to your needs.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{

		// this is for testing purposes, modify apiUrl, uuid and dataSet
		// according to your needs
		// if values received from server differ from input, these values will
		// be printed to stdout
		// otherwise no output

		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "4668221206e047648f622dc4690ff7dc";
		String dataSet = "bigcat-labels64-write-2";
		
		Repository repo = new Repository( url, uuid );
		
		try {
			repo.getRootNode().createDataset( dataSet, DatasetBlkLabel.TYPE );
		}
		catch ( Exception e ) {
			e.printStackTrace( System.err );
		}
		
		DatasetBlkLabel ds = new DatasetBlkLabel( repo.getRootNode(), dataSet );

		Random rng = new Random();

		int[] dim = new int[] { 200, 200, 96 };
		long[] longDim = new long[ dim.length ];
		for ( int i = 0; i < longDim.length; i++ )
			longDim[ i ] = dim[ i ];
		ArrayImg< UnsignedLongType, ? > ref = new ArrayImgFactory< UnsignedLongType >().create( longDim, new UnsignedLongType() );
		for ( UnsignedLongType r : ref )
			r.set( rng.nextLong() );

		DvidLabels64Writer writer = new DvidLabels64Writer( ds );
		int[] steps = new int[] { 200, 200, 32 };
		int[] offset = new int[] { 1, 15, 65 };
//		offset = new int[] { 32, 64, 96 };
		
		writer.writeImage( ref, steps, offset );

		// read image from dvid server
		ArrayImg< UnsignedLongType, ? > target = new ArrayImgFactory< UnsignedLongType >().create( longDim, new UnsignedLongType() );
		ds.get( target, offset );
		
		ArrayCursor< UnsignedLongType > t = target.cursor();
		for ( UnsignedLongType r : Views.flatIterable( ref ) )
		{
			long comp = r.getIntegerLong();
			long test = t.next().getIntegerLong();
			if ( test != comp )
			{
				System.out.println( test + " " + comp );
				System.exit( 9001 );
			}
		}
		System.out.println( "Done." );
	}

}
