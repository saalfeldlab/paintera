/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.img.h5;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.Gson;

import bdv.labels.labelset.Label;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.TLongHashSet;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5UtilsTest
{
	static private String testDirPath = System.getProperty( "user.home" ) + "/tmp/bigcat-test/";

	static private String testH5Name = "test.h5";

	static private TLongLongHashMap lut = new TLongLongHashMap(
			Constants.DEFAULT_CAPACITY,
			Constants.DEFAULT_LOAD_FACTOR,
			Label.TRANSPARENT,
			Label.TRANSPARENT );

	static private long[][] lutExamples = new long[][] {
			{ 1, 2 },
			{ 3, 4 },
			{ 5, 6 },
			{ 7, 8 }
	};

	static private long[] setExamples = new long[] {
			3, 7, 20, -10, 13
	};

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		final File testDir = new File( testDirPath );
		testDir.mkdirs();
		if ( !( testDir.exists() && testDir.isDirectory() ) )
			throw new IOException( "Could not create test directory for HDF5Utils test." );

		for ( final long[] entry : lutExamples )
			lut.put( entry[ 0 ], entry[ 1 ] );

		System.out.println( "lut: " + new Gson().toJson( lut ) );
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void rampDownAfterClass() throws Exception
	{
		final File testDir = new File( testDirPath );
		final File testFile = new File( testDirPath + testH5Name );
		testFile.delete();
		testDir.delete();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
	{}

	@Test
	public void testSaveAndLoadLongLongLut()
	{
		H5Utils.saveLongLongLut( lut, testDirPath + testH5Name, "/lut", 4 );

		final TLongLongHashMap lutMap = H5Utils.loadLongLongLut( testDirPath + testH5Name, "/lut", 2 );
		final long[] keys = lutMap.keys();

		System.out.println( "loaded lut: " + new Gson().toJson( lutMap ) );

		for ( final long key : keys )
			if ( lut.get( key ) != lutMap.get( key ) )
				fail( "loaded lut key '" + key + "' does not match lut." );
	}

	@Test
	public void testSaveAndLoadLongAttribute()
	{
		H5Utils.saveUint64Attribute( 50L, testDirPath + testH5Name, "/", "id" );

		final Long id = H5Utils.loadAttribute( testDirPath + testH5Name, "/", "id" );

		if ( id.longValue() != 50 )
			fail( "Saving and loading long failed." );
	}

	@Test
	public void testSaveAndLoadLongCollection()
	{
		final TLongHashSet expected = new TLongHashSet( setExamples );
		H5Utils.saveLongCollection( expected, testDirPath + testH5Name, "/set", 3 );

		final TLongHashSet test = new TLongHashSet();
		H5Utils.loadLongCollection( test, testDirPath + testH5Name, "/set", 3 );

		for ( final long testValue : test.toArray() )
			assertTrue( "loaded test value '" + testValue + "' does not exist.", expected.contains( testValue ) );

		for ( final long expectedValue : expected.toArray() )
			assertTrue( "loaded expected value '" + expectedValue + "' does not exist.", test.contains( expectedValue ) );
	}
}
