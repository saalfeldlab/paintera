package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.DoubleStream;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import bdv.labels.labelset.Label;
import bdv.util.IdService;
import bdv.util.N5IdService;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import javafx.stage.DirectoryChooser;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class BackendDialogN5 extends BackendDialogGroupAndDataset implements CombinesErrorMessages
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String AXIS_ORDER_KEY = "axisOrder";

	private static final String ATTRIBUTES_JSON = "attributes.json";

	public BackendDialogN5()
	{
		super( "N5 group", "Dataset", ( group, scene ) -> {
			final DirectoryChooser directoryChooser = new DirectoryChooser();
			final File initDir = new File( group );
			directoryChooser.setInitialDirectory( initDir.exists() && initDir.isDirectory() ? initDir : new File( System.getProperty( "user.home" ) ) );
			final File directory = directoryChooser.showDialog( scene.getWindow() );
			return directory == null ? null : directory.getAbsolutePath();
		} );
	}

	private static boolean isLabelType( final DataType type )
	{
		return isLabelMultisetType( type ) || isIntegerType( type );
	}

	private static boolean isLabelMultisetType( final DataType type )
	{
		return false;
	}

	private static boolean isIntegerType( final DataType type )
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

	private static double minForType( final DataType t )
	{
		// TODO ever return non-zero here?
		switch ( t )
		{
		default:
			return 0.0;
		}
	}

	private static double maxForType( final DataType t )
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

	@SuppressWarnings( "unchecked" )
	@Override
	public < T extends NativeType< T >, V extends Volatile< T > > Pair< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[] > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = groupProperty.get();
		final N5FSReader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		try
		{
			if ( reader.datasetExists( dataset ) )
			{
				final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
				final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
				return new ValuePair<>( new RandomAccessibleInterval[] { raw }, new RandomAccessibleInterval[] { vraw } );
			}
		}
		catch ( final IOException e )
		{

		}
		final String[] scaleDatasets = listScaleDatasets( reader, dataset );
		sortScaleDatasets( scaleDatasets );

		LOG.info( "Opening directories {} as multi-scale in {}: ", Arrays.toString( scaleDatasets ), dataset );

		final RandomAccessibleInterval< T >[] raw = new RandomAccessibleInterval[ scaleDatasets.length ];
		final RandomAccessibleInterval< V >[] vraw = new RandomAccessibleInterval[ scaleDatasets.length ];
		for ( int scale = 0; scale < scaleDatasets.length; ++scale )
		{
			LOG.debug( "Populating scale level {}", scale );
			raw[ scale ] = N5Utils.openVolatile( reader, dataset + "/" + scaleDatasets[ scale ] );
			vraw[ scale ] = VolatileViews.wrapAsVolatile( raw[ scale ], sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
			LOG.debug( "Populated scale level {}", scale );
		}
		return new ValuePair<>( raw, vraw );
	}

	@Override
	public boolean isLabelType() throws IOException
	{
		return isLabelType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isLabelMultisetType() throws IOException
	{
		return isLabelMultisetType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public boolean isIntegerType() throws IOException
	{
		return isIntegerType( new N5FSReader( groupProperty.get() ).getDatasetAttributes( dataset.get() ).getDataType() );
	}

	@Override
	public void updateDatasetInfo( final String basePath, final DatasetInfo info )
	{
		try
		{
			final String n5Path = groupProperty.get();
			final N5FSReader reader = new N5FSReader( n5Path );

			final String dataset = getAttributesContainingPath( reader, basePath );

			LOG.debug( "Got dataset {} for base path {} in {}", dataset, basePath, n5Path );

			final DatasetAttributes dsAttrs = reader.getDatasetAttributes( dataset );
			final int nDim = dsAttrs.getNumDimensions();

			final HashMap< String, JsonElement > attributes = reader.getAttributes( dataset );

			if ( attributes.containsKey( AXIS_ORDER_KEY ) )
			{
				final AxisOrder ao = reader.getAttribute( dataset, AXIS_ORDER_KEY, AxisOrder.class );
				datasetInfo.defaultAxisOrderProperty().set( ao );
				datasetInfo.selectedAxisOrderProperty().set( ao );
			}
			else
			{
				final Optional< AxisOrder > ao = AxisOrder.defaultOrder( nDim );
				if ( ao.isPresent() )
					this.datasetInfo.defaultAxisOrderProperty().set( ao.get() );
				if ( this.datasetInfo.selectedAxisOrderProperty().isNull().get() || this.datasetInfo.selectedAxisOrderProperty().get().numDimensions() != nDim )
					this.axisOrder().set( ao.get() );
			}

			this.datasetInfo.setResolution( Optional.ofNullable( reader.getAttribute( dataset, RESOLUTION_KEY, double[].class ) ).orElse( DoubleStream.generate( () -> 1.0 ).limit( nDim ).toArray() ) );
			this.datasetInfo.setOffset( Optional.ofNullable( reader.getAttribute( dataset, OFFSET_KEY, double[].class ) ).orElse( new double[ nDim ] ) );
			this.datasetInfo.minProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MIN_KEY, Double.class ) ).orElse( minForType( dsAttrs.getDataType() ) ) );
			this.datasetInfo.maxProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MAX_KEY, Double.class ) ).orElse( maxForType( dsAttrs.getDataType() ) ) );

		}
		catch ( final IOException e )
		{

		}

	}

	@Override
	public List< String > discoverDatasetAt( final String at )
	{
		final ArrayList< String > datasets = new ArrayList<>();
		try
		{
			final N5FSReader n5 = new N5FSReader( at );
			discoverSubdirectories( n5, "", datasets, () -> this.isTraversingDirectories.set( false ) );
		}
		catch ( final IOException e ) {}

		return datasets;
	}

	public static void discoverSubdirectories( final N5Reader n5, final String pathName, final Collection< String > datasets, final Runnable onInterruption )
	{
		if ( !Thread.currentThread().isInterrupted() )
		{
			try
			{
				final String[] groups = n5.list( pathName );
				Arrays.sort( groups );
				for ( final String group : groups ) {
					final String absolutePathName = pathName + "/" + group;
					if ( n5.datasetExists( absolutePathName ) )
						datasets.add( absolutePathName );
					else {
						final String[] scales = n5.list( absolutePathName );
						boolean isMipmapGroup = scales.length > 0;
						for ( final String scale : scales )
						{
							if ( !( scale.matches( "^s[0-9]+$" ) && n5.datasetExists( absolutePathName + "/" + scale ) ) )
							{
								isMipmapGroup = false;
								break;
							}
						}
						if (isMipmapGroup)
							datasets.add( absolutePathName );
						else
							discoverSubdirectories( n5, absolutePathName, datasets, onInterruption );
					}
				}
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else
			onInterruption.run();
	}

	public static String[] listScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		return Arrays
				.stream( n5.list( group ) )
				.filter( s -> s.matches( "^s\\d+$" ) )
				.filter( s -> { try { return n5.datasetExists( group + "/" + s ); } catch ( final IOException e ) { return false; } } )
				.toArray( String[]::new );
	}

	public static void sortScaleDatasets( final String[] scaleDatasets )
	{
		Arrays.sort( scaleDatasets, ( f1, f2 ) -> {
			return Integer.compare(
					Integer.parseInt( f1.replaceAll( "[^\\d]", "" ) ),
					Integer.parseInt( f2.replaceAll( "[^\\d]", "" ) ) );
		} );
	}

	public String getAttributesContainingPath( final N5Reader reader, final String basePath ) throws IOException
	{
		if ( reader.datasetExists( basePath ) )
			return basePath;
		final String[] scaleDirs = listScaleDatasets( reader, basePath );
		sortScaleDatasets( scaleDirs );
		LOG.debug( "Got the following scale dirs: {}", Arrays.toString( scaleDirs ) );
		return basePath + "/" + scaleDirs[ 0 ];
	}

	@Override
	public IdService idService()
	{
		try
		{
			final String group = groupProperty.get();
			final N5Writer n5 = new N5FSWriter( group );
			final String dataset = this.dataset.get();

			Long maxId;
			maxId = n5.getAttribute( dataset, "maxId", Long.class );
			final long actualMaxId;
			if ( maxId == null )
			{
				if ( isIntegerType() )
					actualMaxId = maxId( n5, dataset );
				else
					// TODO deal with LabelMultisetType here
					return null;
			}
			else
				actualMaxId = maxId;
			return new N5IdService( n5, dataset, actualMaxId );

		}
		catch ( final IOException e )
		{
			return null;
		}
	}

	private static < T extends IntegerType< T > & NativeType< T > > long maxId( final N5Reader n5, final String dataset ) throws IOException
	{
		final RandomAccessibleInterval< T > data = N5Utils.open( n5, dataset );
		long maxId = 0;
		for ( final T label : Views.flatIterable( data ) )
			maxId = IdService.max( label.getIntegerLong(), maxId );
		return maxId;
	}

	@Override
	public Consumer< RandomAccessibleInterval< UnsignedLongType > > commitCanvas()
	{
		return canvas -> {
			try
			{
				final N5FSWriter n5 = new N5FSWriter( this.groupProperty.get() );
				final String dataset = this.dataset.get();
				// TODO do multi scale!

				if ( isIntegerType() )
					commitForIntegerType( n5, dataset, canvas );
			}
			catch ( final IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		};
	}

	private static < T extends IntegerType< T > & NativeType< T > > void commitForIntegerType(
			final N5Writer n5,
			final String dataset,
			final RandomAccessibleInterval< UnsignedLongType > canvas ) throws IOException
	{

		// TODO DO MULTISCALE!
		// TODO DO TIME?
		LOG.debug( "Commiting canvas for interval {} {}", Arrays.toString( Intervals.minAsLongArray( canvas ) ), Arrays.toString( Intervals.maxAsDoubleArray( canvas ) ) );
		final RandomAccessibleInterval< T > labels = N5Utils.open( n5, dataset );
		final DatasetAttributes attributes = n5.getDatasetAttributes( dataset );
		final int[] blockSize = attributes.getBlockSize();
		final long[] dims = attributes.getDimensions();

		final long[] blockAlignedMin = new long[ labels.numDimensions() ];
		final long[] blockAlignedMax = new long[ labels.numDimensions() ];

		for ( int d = 0; d < blockAlignedMin.length; ++d )
		{
			blockAlignedMin[ d ] = canvas.min( d ) / blockSize[ d ] * blockSize[ d ];
			blockAlignedMax[ d ] = Math.min( ( canvas.max( d ) / blockSize[ d ] + 1 ) * blockSize[ d ], dims[ d ] ) - 1;
		}

		final FinalInterval blockAlignedInterval = new FinalInterval( blockAlignedMin, blockAlignedMax );

		final ArrayImg< T, ? > intervalCopy = new ArrayImgFactory< T >().create( Intervals.dimensionsAsLongArray( blockAlignedInterval ), Util.getTypeFromInterval( labels ).createVariable() );

		for ( Cursor< T > s = Views.flatIterable( Views.interval( labels, blockAlignedInterval ) ).cursor(), t = Views.flatIterable( intervalCopy ).cursor(); s.hasNext(); )
			t.next().set( s.next() );

		final Cursor< UnsignedLongType > s = Views.flatIterable( canvas ).cursor();
		final Cursor< T > t = Views.flatIterable( Views.interval( Views.translate( intervalCopy, blockAlignedMin ), canvas ) ).cursor();
		while ( s.hasNext() )
		{
			final long label = s.next().get();
			t.fwd();
			if ( Label.regular( label ) )
				t.get().setInteger( label );
		}

		final long[] gridOffset = new long[ intervalCopy.numDimensions() ];
		for ( int d = 0; d < gridOffset.length; ++d )
			gridOffset[ d ] = blockAlignedMin[ d ] / blockSize[ d ];

		N5Utils.saveBlock( Views.translate( intervalCopy, blockAlignedMin ), n5, dataset, gridOffset );

	}

}
