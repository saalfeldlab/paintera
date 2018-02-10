package bdv.bigcat.viewer.atlas.opendialog;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.DoubleStream;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.net.imglib2.util.Triple;
import bdv.net.imglib2.util.ValueTriple;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;

public class BackendDialogHDF5 extends BackendDialogGroupAndDataset implements CombinesErrorMessages
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final String RESOLUTION_KEY = "resolution";

	private static final String OFFSET_KEY = "offset";

	private static final String MIN_KEY = "min";

	private static final String MAX_KEY = "max";

	private static final String AXIS_ORDER_KEY = "axisOrder";

	private static final int MAX_DEFAULT_BLOCK_SIZE = 64;

	private final StringBinding name = Bindings.createStringBinding( () -> {
		final String[] entries = Optional
				.ofNullable( dataset )
				.map( d -> d.get().split( "/" ) )
				.map( a -> a.length > 0 ? a : new String[] { null } )
				.orElse( new String[] { null } );
		return entries[ entries.length - 1 ];
	}, dataset );

	public BackendDialogHDF5()
	{
		super( "HDF file", "Dataset", ( group, scene ) -> {
			final FileChooser fileChooser = new FileChooser();
//			fileChooser.setInitialDirectory( new File( group ) );
			fileChooser.getExtensionFilters().addAll( new ExtensionFilter( "HDF5 Files", "*.hdf", "*.h5" ) );
			final File file = fileChooser.showOpenDialog( scene.getWindow() );
			return file.getAbsolutePath();
		} );
	}

	@Override
	public < T extends NativeType< T >, V extends Volatile< T > > Triple< RandomAccessibleInterval< T >[], RandomAccessibleInterval< V >[], AffineTransform3D[] > getDataAndVolatile(
			final SharedQueue sharedQueue,
			final int priority ) throws IOException
	{
		final String group = groupProperty.get();
		final String dataset = this.dataset.get();
		LOG.debug( "Opening dataset: {}/{}", group, dataset );
		final double[] resolution = Arrays.stream( resolution() ).mapToDouble( DoubleProperty::get ).toArray();
		final N5HDF5Reader n5 = getDefaultChunksizeReader( group, dataset, defaultBlockSize( MAX_DEFAULT_BLOCK_SIZE, resolution ) );
		// TODO optimize block size
		// TODO do multiscale
		final RandomAccessibleInterval< T > raw = N5Utils.openVolatile( n5, dataset );
		final RandomAccessibleInterval< V > vraw = VolatileViews.wrapAsVolatile( raw, sharedQueue, new CacheHints( LoadingStrategy.VOLATILE, priority, true ) );
		final AffineTransform3D transform = new AffineTransform3D();
		final double[] offset = Arrays.stream( offset() ).mapToDouble( DoubleProperty::get ).toArray();
		LOG.debug( "Setting resolution {} and offset {}", Arrays.toString( resolution ), Arrays.toString( offset ) );
		transform.set(
				resolution[ 0 ], 0, 0, offset[ 0 ],
				0, resolution[ 1 ], 0, offset[ 1 ],
				0, 0, resolution[ 2 ], offset[ 2 ] );
		return new ValueTriple<>( new RandomAccessibleInterval[] { raw }, new RandomAccessibleInterval[] { vraw }, new AffineTransform3D[] { transform } );
	}

	@Override
	public boolean isLabelType() throws IOException
	{
		final N5Reader reader = new N5HDF5Reader( groupProperty.get() );
		final DatasetAttributes attributes = reader.getDatasetAttributes( dataset.get() );
		return N5Helpers.isLabelType( attributes.getDataType() );
	}

	@Override
	public boolean isLabelMultisetType() throws IOException
	{
		final N5Reader reader = new N5HDF5Reader( groupProperty.get() );
		final DatasetAttributes attributes = reader.getDatasetAttributes( dataset.get() );
		return N5Helpers.isLabelMultisetType( attributes.getDataType() );
	}

	@Override
	public boolean isIntegerType() throws IOException
	{
		final N5Reader reader = new N5HDF5Reader( groupProperty.get() );
		final DatasetAttributes attributes = reader.getDatasetAttributes( dataset.get() );
		return N5Helpers.isIntegerType( attributes.getDataType() );
	}

	@Override
	public void updateDatasetInfo( final String dataset, final DatasetInfo info )
	{
		try
		{
			final N5Reader reader = new N5HDF5Reader( groupProperty.get() );
			final DatasetAttributes dsAttrs = reader.getDatasetAttributes( dataset );
			final int nDim = dsAttrs.getNumDimensions();

			setResolution( Optional.ofNullable( reader.getAttribute( dataset, RESOLUTION_KEY, double[].class ) ).map( BackendDialogHDF5::revert ).orElse( DoubleStream.generate( () -> 1.0 ).limit( nDim ).toArray() ) );
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
		N5HDF5Reader reader;
		try
		{
			reader = new N5HDF5Reader( at );
			final List< String > datasets = new ArrayList<>();
			final Stack< String > candidates = new Stack<>();
			candidates.push( "" );
			while ( !candidates.isEmpty() )
			{
				final String candidate = candidates.pop();
				if ( reader.datasetExists( candidate ) )
					datasets.add( candidate );
				else
					Arrays.stream( reader.list( candidate ) ).forEach( c -> candidates.add( candidate + "/" + c ) );
			}
			return datasets;
		}
		catch ( final IOException e )
		{
			throw new RuntimeException( e );
		}
	}

	public static double[] invert( final double[] array )
	{
		final double[] ret = new double[ array.length ];
		for ( int i = 0, k = array.length - 1; i < array.length; ++i, --k )
			ret[ k ] = array[ i ];
		return ret;
	}

	@Override
	public String identifier()
	{
		return "HDF5";
	}

	@Override
	public ObservableStringValue nameProperty()
	{
		return this.name;
	}

	private static int[] defaultBlockSize( final int maxBlockSize, final double[] scales )
	{
		final double scaleMin = Arrays.stream( scales ).min().getAsDouble();
		final int[] blockSize = Arrays
				.stream( scales )
				.map( r -> scaleMin / r )
				.mapToInt( factor -> Math.max( ( int ) Math.round( factor * maxBlockSize ), 1 ) )
				.toArray();
		LOG.debug( "Setting default block size to {}", Arrays.toString( blockSize ) );
		return blockSize;
	}

	private static final double[] revert( final double[] array )
	{
		final double[] reverted = new double[ array.length ];
		return revert( array, reverted );
	}

	private static final double[] revert( final double[] array, final double[] reverted )
	{
		for ( int i = 0; i < array.length; ++i )
			reverted[ i ] = array[ array.length - 1 - i ];
		return reverted;
	}

	private static N5HDF5Reader getDefaultChunksizeReader(
			final String group,
			final String dataset,
			final int[] defaultBlockSize ) throws IOException
	{
		final N5HDF5Reader reader = new N5HDF5Reader( group );
		final DatasetAttributes attrs = reader.getDatasetAttributes( dataset );
		final int[] blockSize = attrs.getBlockSize().clone();

		boolean overrideBlockSize = false;
		for ( int d = 0; d < blockSize.length; ++d )
			if ( blockSize[ d ] > 2 * defaultBlockSize[ d ] || defaultBlockSize[ d ] > 2 * blockSize[ d ] )
			{
				blockSize[ d ] = defaultBlockSize[ d ];
				overrideBlockSize = true;
			}

		if ( overrideBlockSize )
			LOG.warn(
					"Block size {} not optimized for viewing -- using {} instead. Consider re-chunking data.",
					Arrays.toString( attrs.getBlockSize() ),
					Arrays.toString( blockSize ) );

		return new N5HDF5Reader( group, overrideBlockSize, blockSize );
	}

}
