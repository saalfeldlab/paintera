package bdv.img.dvid;

import java.io.IOException;
import java.util.Random;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import bdv.util.dvid.DatasetBlk;
import bdv.util.dvid.DatasetBlkUint8;
import bdv.util.dvid.Repository;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 * 
 *         Write any {@link RandomAccessibleInterval} of type {@link RealType}
 *         into an existing dvid repository/dataset.
 *
 */
public class DvidImage8Writer extends AbstractDvidImageWriter< UnsignedByteType >
{

	/**
	 * @param url
	 *            Url to dvid server in the form of http://hostname:port
	 * @param uuid
	 *            Uuid of repository within the dvid server specified by apiUrl.
	 *            The root node of the repository will be used for writing.
	 * @param dataSet
	 *            Name of the data set within the repository specified by uuid.
	 *            This data set must be of type imageblk.
	 * 
	 *            This calls
	 *            {@link DvidImage8Writer#DvidLabels64ByteWriter(String, String, String, int)}
	 *            with a default block size of 32.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 **/
	public DvidImage8Writer( String url, String uuid, String dataSet )
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
	 *            This data set must be of type imageblk
	 * @param blockSize
	 *            Block size of the data set. Must suit block size stored in
	 *            dvid server.
	 * @throws IOException 
	 * @throws JsonIOException 
	 * @throws JsonSyntaxException 
	 */
	public DvidImage8Writer( String url, String uuid, final String dataSetName, final int[] blockSize )
			throws JsonSyntaxException, JsonIOException, IOException
	{
		this( new DatasetBlkUint8( new Repository( url, uuid ).getRootNode(), dataSetName ), blockSize );
	}
	
	public DvidImage8Writer( DatasetBlkUint8 dataset, int[] blockSize )
	{
		super( dataset, blockSize );
	}
	
	public DvidImage8Writer( DatasetBlkUint8 dataset ) throws JsonSyntaxException, JsonIOException, IOException
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
		String dataSet = "bigcat-uint8-write-3";
		
		Repository repo = new Repository( url, uuid );
		
		try {
			repo.getRootNode().createDataset( dataSet, DatasetBlkUint8.TYPE );
		}
		catch ( Exception e ) {
			e.printStackTrace( System.err );
		}
		
		DatasetBlkUint8 ds = new DatasetBlkUint8( repo.getRootNode(), dataSet );

		Random rng = new Random();

		int[] dim = new int[] { 200, 200, 96 };
		long[] longDim = new long[ dim.length ];
		for ( int i = 0; i < longDim.length; i++ )
			longDim[ i ] = dim[ i ];
		ArrayImg< UnsignedByteType, ByteArray > ref = ArrayImgs.unsignedBytes( longDim );
		for ( UnsignedByteType r : ref )
			r.set( rng.nextInt( 0xff ) );

		DvidImage8Writer writer = new DvidImage8Writer( ds );
		int[] steps = new int[] { 200, 200, 32 };
		int[] offset = new int[] { 1, 15, 65 };
//		offset = new int[] { 32, 64, 96 };
		
		writer.writeImage( ref, steps, offset );

		// read image from dvid server
		ArrayImg< UnsignedByteType, ByteArray > target = ArrayImgs.unsignedBytes( longDim );
		ds.get( target, offset );
		
		ArrayCursor< UnsignedByteType > t = target.cursor();
		for ( UnsignedByteType r : Views.flatIterable( ref ) )
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
