package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class N5Helpers
{

	private static final String MULTI_SCALE_KEY = "multiScale";

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	public static boolean isLabelType( final DataType type )
	{
		return N5Helpers.isLabelMultisetType( type ) || N5Helpers.isIntegerType( type );
	}

	public static boolean isIntegerType( final DataType type )
	{
		switch ( type )
		{
		case INT8:
		case INT16:
		case INT32:
		case INT64:
		case UINT8:
		case UINT16:
		case UINT32:
		case UINT64:
			return true;
		default:
			return false;
		}
	}

	public static boolean isLabelMultisetType( final DataType type )
	{
		return false;
	}

	public static double minForType( final DataType t )
	{
		// TODO ever return non-zero here?
		switch ( t )
		{
		default:
			return 0.0;
		}
	}

	public static double maxForType( final DataType t )
	{
		switch ( t )
		{
		case UINT8:
			return 0xff;
		case UINT16:
			return 0xffff;
		case UINT32:
			return 0xffffffffl;
		case UINT64:
			return 2.0 * Long.MAX_VALUE;
		case INT8:
			return Byte.MAX_VALUE;
		case INT16:
			return Short.MAX_VALUE;
		case INT32:
			return Integer.MAX_VALUE;
		case INT64:
			return Long.MAX_VALUE;
		case FLOAT32:
		case FLOAT64:
			return 1.0;
		default:
			return 1.0;
		}
	}

	public static String[] listScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		final String[] scaleDirs = Arrays
				.stream( n5.list( group ) )
				.filter( s -> s.matches( "^s\\d+$" ) )
				.filter( s -> {
					try
					{
						return n5.datasetExists( group + "/" + s );
					}
					catch ( final IOException e )
					{
						return false;
					}
				} )
				.toArray( String[]::new );

		LOG.debug( "Found these scale dirs: {}", Arrays.toString( scaleDirs ) );
		return scaleDirs;
	}

	public static String[] listAndSortScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		final String[] scaleDirs = listScaleDatasets( n5, group );
		sortScaleDatasets( scaleDirs );

		LOG.debug( "Sorted scale dirs: {}", Arrays.toString( scaleDirs ) );
		return scaleDirs;
	}

	public static void sortScaleDatasets( final String[] scaleDatasets )
	{
		Arrays.sort( scaleDatasets, ( f1, f2 ) -> {
			return Integer.compare(
					Integer.parseInt( f1.replaceAll( "[^\\d]", "" ) ),
					Integer.parseInt( f2.replaceAll( "[^\\d]", "" ) ) );
		} );
	}

	public static N5Reader n5Reader( final String base, final int... defaultCellDimensions ) throws IOException
	{
		return isHDF( base ) ? new N5HDF5Reader( base, defaultCellDimensions ) : new N5FSReader( base );
	}

	public static N5Writer n5Writer( final String base, final int... defaultCellDimensions ) throws IOException
	{
		return isHDF( base ) ? new N5HDF5Writer( base, defaultCellDimensions ) : new N5FSWriter( base );
	}

	public static boolean isHDF( final String base )
	{
		LOG.debug( "Checking {} for HDF", base );
		final boolean isHDF = Pattern.matches( "^h5://", base ) || Pattern.matches( "^.*\\.(hdf|h5)$", base );
		LOG.debug( "{} is hdf5? {}", base, isHDF );
		return isHDF;
	}

	public static List< String > discoverDatasets( final N5Reader n5, final Runnable onInterruption )
	{
		final List< String > datasets = new ArrayList<>();
		discoverSubdirectories( n5, "", datasets, onInterruption );
		return datasets;
	}

	public static void discoverSubdirectories( final N5Reader n5, final String pathName, final Collection< String > datasets, final Runnable onInterruption )
	{
		if ( !Thread.currentThread().isInterrupted() )
		{
			try
			{
				if ( isDataset( n5, pathName ) )
					datasets.add( pathName );
				else
					Arrays
							.stream( n5.list( pathName ) )
							.map( subGroup -> Paths.get( pathName, subGroup ).toString() )
							.forEach( g -> discoverSubdirectories( n5, g, datasets, onInterruption ) );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			onInterruption.run();
		}
	}

	public static boolean isDataset( final N5Reader n5, final String path ) throws IOException
	{
		return n5.datasetExists( path ) || isMultiScale( n5, path );
	}

	public static boolean isMultiScale( final N5Reader n5, final String path ) throws IOException
	{
		return Optional.ofNullable( n5.getAttribute( path, MULTI_SCALE_KEY, Boolean.class ) ).orElse( false );
	}

}
