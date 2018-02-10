package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import bdv.labels.labelset.Label;
import bdv.net.imglib2.util.Triple;
import bdv.net.imglib2.util.ValueTriple;
import bdv.util.IdService;
import bdv.util.N5IdService;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.stage.DirectoryChooser;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
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

	private final StringBinding name = Bindings.createStringBinding( () -> {
		final String[] entries = Optional
				.ofNullable( dataset )
				.map( d -> d.get().split( "/" ) )
				.map( a -> a.length > 0 ? a : new String[] { null } )
				.orElse( new String[] { null } );
		return entries[ entries.length - 1 ];
	}, dataset );

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

	@SuppressWarnings( "unchecked" )
	@Override
	public < T extends NativeType< T >, V extends Volatile< T > > Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = groupProperty.get();
		final N5Reader reader = new N5FSReader( group );
		final String dataset = this.dataset.get();
		try
		{
			if ( reader.datasetExists( dataset ) )
			{
				final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( reader, dataset );
				final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
				final double[] resolution = Arrays.stream( resolution() ).mapToDouble( DoubleProperty::get ).toArray();
				final double[] offset = Arrays.stream( offset() ).mapToDouble( DoubleProperty::get ).toArray();
				final AffineTransform3D transform = new AffineTransform3D();
				transform.set(
						resolution[ 0 ], 0, 0, offset[ 0 ],
						0, resolution[ 1 ], 0, offset[ 1 ],
						0, 0, resolution[ 2 ], offset[ 2 ] );
				LOG.debug( "Resolution={}", Arrays.toString( resolution ) );
				return new ValueTriple<>( new RandomAccessibleInterval[] { raw }, new RandomAccessibleInterval[] { vraw }, new AffineTransform3D[] { transform } );
			}
		}
		catch ( final IOException e )
		{

		}
		final String[] scaleDatasets = listScaleDatasets( reader, dataset );
		sortScaleDatasets( scaleDatasets );

		LOG.debug( "Opening directories {} as multi-scale in {}: ", Arrays.toString( scaleDatasets ), dataset );

		final RandomAccessibleInterval< T >[] raw = new RandomAccessibleInterval[ scaleDatasets.length ];
		final RandomAccessibleInterval< V >[] vraw = new RandomAccessibleInterval[ scaleDatasets.length ];
		final AffineTransform3D[] transforms = new AffineTransform3D[ scaleDatasets.length ];
		final double[] initialResolution = Arrays.stream( resolution() ).mapToDouble( DoubleProperty::get ).toArray();
		final double[] initialDonwsamplingFactors = Optional.ofNullable( reader.getAttribute( dataset + "/" + scaleDatasets[ 0 ], "downsamplingFactors", double[].class ) ).orElse( new double[] { 1, 1, 1 } );
		final double[] offset = Arrays.stream( offset() ).mapToDouble( DoubleProperty::get ).toArray();
		LOG.debug( "Initial resolution={}", Arrays.toString( initialResolution ) );
		for ( int scale = 0; scale < scaleDatasets.length; ++scale )
		{
			LOG.debug( "Populating scale level {}", scale );
			final String scaleDataset = dataset + "/" + scaleDatasets[ scale ];
			raw[ scale ] = N5Utils.openVolatile( reader, scaleDataset );
			vraw[ scale ] = VolatileViews.wrapAsVolatile( raw[ scale ], sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );

			final double[] downsamplingFactors = Optional.ofNullable( reader.getAttribute( scaleDataset, "downsamplingFactors", double[].class ) ).orElse( new double[] { 1, 1, 1 } );

			final double[] scaledResolution = new double[ downsamplingFactors.length ];
			final double[] shift = new double[ downsamplingFactors.length ];

			for ( int d = 0; d < downsamplingFactors.length; ++d )
			{
				scaledResolution[ d ] = downsamplingFactors[ d ] * initialResolution[ d ];
				shift[ d ] = 0.5 / initialDonwsamplingFactors[ d ] - 0.5 / downsamplingFactors[ d ];
			}

			LOG.debug( "Downsampling factors={}, scaled resolution={}", Arrays.toString( downsamplingFactors ), Arrays.toString( scaledResolution ) );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					scaledResolution[ 0 ], 0, 0, offset[ 0 ],
					0, scaledResolution[ 1 ], 0, offset[ 1 ],
					0, 0, scaledResolution[ 2 ], offset[ 2 ] );
			transforms[ scale ] = transform.concatenate( new Translation3D( shift ) );
			LOG.debug( "Populated scale level {}", scale );
		}
		return new ValueTriple<>( raw, vraw, transforms );
	}

	@Override
	public boolean isLabelType() throws IOException
	{
		return N5Helpers.isLabelType( getDataType() );
	}

	@Override
	public boolean isLabelMultisetType() throws IOException
	{
		return N5Helpers.isLabelMultisetType( getDataType() );
	}

	@Override
	public boolean isIntegerType() throws IOException
	{
		return N5Helpers.isIntegerType( getDataType() );
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

			setResolution( Optional.ofNullable( reader.getAttribute( dataset, RESOLUTION_KEY, double[].class ) ).orElse( DoubleStream.generate( () -> 1.0 ).limit( nDim ).toArray() ) );
			setOffset( Optional.ofNullable( reader.getAttribute( dataset, OFFSET_KEY, double[].class ) ).orElse( new double[ nDim ] ) );
			this.datasetInfo.minProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MIN_KEY, Double.class ) ).orElse( N5Helpers.minForType( dsAttrs.getDataType() ) ) );
			this.datasetInfo.maxProperty().set( Optional.ofNullable( reader.getAttribute( dataset, MAX_KEY, Double.class ) ).orElse( N5Helpers.maxForType( dsAttrs.getDataType() ) ) );

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
		catch ( final IOException e )
		{}

		return datasets;
	}

	public static void discoverSubdirectories( final N5Reader n5, final String pathName, final Collection< String > datasets, final Runnable onInterruption )
	{
		if ( !Thread.currentThread().isInterrupted() )
			try
			{
				final String[] groups = n5.list( pathName );
				Arrays.sort( groups );
				for ( final String group : groups )
				{
					final String absolutePathName = pathName + "/" + group;
					if ( n5.datasetExists( absolutePathName ) )
						datasets.add( absolutePathName );
					else
					{
						final String[] scales = n5.list( absolutePathName );
						boolean isMipmapGroup = scales.length > 0;
						for ( final String scale : scales )
							if ( !( scale.matches( "^s[0-9]+$" ) && n5.datasetExists( absolutePathName + "/" + scale ) ) )
							{
								isMipmapGroup = false;
								break;
							}
						if ( isMipmapGroup )
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
		else
			onInterruption.run();
	}

	public static String[] listScaleDatasets( final N5Reader n5, final String group ) throws IOException
	{
		return Arrays
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

			final Long maxId = n5.getAttribute( dataset, "maxId", Long.class );
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
		final String ds;
		if ( n5.datasetExists( dataset ) )
			ds = dataset;
		else
		{
			final String[] scaleDirs = listScaleDatasets( n5, dataset );
			sortScaleDatasets( scaleDirs );
			ds = Paths.get( dataset, scaleDirs ).toString();
		}
		final RandomAccessibleInterval< T > data = N5Utils.open( n5, ds );
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

	@Override
	public String identifier()
	{
		return "N5";
	}

	public DataType getDataType() throws IOException
	{
		return getAttributes().getDataType();
	}

	public DatasetAttributes getAttributes() throws IOException
	{
		final String ds = dataset.get();
		final String group = groupProperty.get();
		final N5FSReader reader = new N5FSReader( group );

		if ( reader.datasetExists( ds ) )
		{
			LOG.debug( "Getting attributes for {} and {}", group, ds );
			return reader.getDatasetAttributes( ds );
		}

		final String[] scaleDirs = listScaleDatasets( reader, ds );

		if ( scaleDirs.length > 0 )
		{
			LOG.debug( "Getting attributes for {} and {}", group, scaleDirs[ 0 ] );
			return reader.getDatasetAttributes( Paths.get( ds, scaleDirs[ 0 ] ).toString() );
		}

		throw new RuntimeException( String.format( "Cannot read dataset attributes for group %s and dataset %s.", group, ds ) );

	}

	@Override
	public ObservableStringValue nameProperty()
	{
		return this.name;
	}

}
