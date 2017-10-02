package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import com.sun.javafx.application.PlatformImpl;

import bdv.AbstractViewerSetupImgLoader;
import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetSourceSpec;
import bdv.bigcat.viewer.atlas.data.HDF5UnsignedByteSpec;
import bdv.bigcat.viewer.viewer3d.Viewer3D;
import bdv.bigcat.viewer.viewer3d.Viewer3DController;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleApplication2
{

	public static void main( final String[] args ) throws Exception
	{
		final String rawFile = "data/sample_B_20160708_frags_46_50.hdf";
		PlatformImpl.startup( () -> {} );
		final String rawDataset = "volumes/raw";
		final String labelsFile = rawFile;
		final String labelsDataset = "/volumes/labels/neuron_ids";

		final double[] resolution = { 4, 4, 40 };
		final double[] offset = { 424, 424, 560 };
		final int[] cellSize = { 145, 53, 5 };

		final HDF5UnsignedByteSpec rawSource = new HDF5UnsignedByteSpec( rawFile, rawDataset, cellSize, resolution, "raw" );

		final double[] min = Arrays.stream( Intervals.minAsLongArray( rawSource.getSource().getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( rawSource.getSource().getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final AffineTransform3D affine = new AffineTransform3D();
		rawSource.getSource().getSourceTransform( 0, 0, affine );
		affine.apply( min, min );
		affine.apply( max, max );

		final Atlas viewer = new Atlas( new FinalInterval( Arrays.stream( min ).mapToLong( Math::round ).toArray(), Arrays.stream( max ).mapToLong( Math::round ).toArray() ) );

		final Viewer3DController controller = new Viewer3DController();
		controller.setMode( Viewer3DController.ViewerMode.ONLY_ONE_NEURON_VISIBLE );

		final CountDownLatch latch = new CountDownLatch( 1 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			stage.show();
			final Viewer3D v3d = new Viewer3D( "appname", 100, 100, false );
			new Thread( () -> v3d.main() ).start();
			viewer.baseView().setInfoNode( v3d.getPanel() );
			controller.setViewer3D( v3d );
			controller.setResolution( resolution );
			latch.countDown();
		} );

		RandomAccessibleInterval< LabelMultisetType > volumeLabels = null;
		final IHDF5Reader reader;
		reader = HDF5Factory.openForReading( labelsFile );
		/** loaded segments */
		ArrayList< H5LabelMultisetSetupImageLoader > labels = null;
		if ( reader.exists( labelsDataset ) )
		{
			labels = bdv.bigcat.viewer.viewer3d.util.HDF5Reader.readLabels( reader, labelsDataset );
		}
		volumeLabels = labels.get( 0 ).getImage( 0 );

		Localizable location = new Point( new int[] { 10, 267, 0 } );

		latch.await();
		controller.generateMesh( volumeLabels, location );

		final Volatile< UnsignedByteType > abc = rawSource.getViewerSource().getType();
		viewer.addRawSource( rawSource, 0., 255. );

		final HDF5LabelMultisetSourceSpec labelSpec2 = new HDF5LabelMultisetSourceSpec( labelsFile, labelsDataset, cellSize, "labels" );
		viewer.addLabelSource( labelSpec2 );

		final boolean demonstrateRemove = false;
		if ( demonstrateRemove )
		{
			final HDF5LabelMultisetSourceSpec labelSpec3 = new HDF5LabelMultisetSourceSpec( labelsFile, labelsDataset, cellSize, "labels2" );
			viewer.addLabelSource( labelSpec3 );

			Platform.runLater( () -> {
				final Dialog< Boolean > d = new Dialog<>();
				final ButtonType removeType = new ButtonType( "Remove extra source", ButtonData.OK_DONE );
				d.getDialogPane().getButtonTypes().add( removeType );
				final Button b = ( Button ) d.getDialogPane().lookupButton( removeType );
				System.out.println( "b " + b );
				d.show();
				d.setOnHiding( event -> {
					System.out.println( "Removing source!" );
					viewer.baseView().requestFocus();
					viewer.removeSource( labelSpec3 );
				} );
				viewer.baseView().requestFocus();
			} );
		}

	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

	public static class ARGBConvertedSource< T > implements Source< VolatileARGBType >
	{
		final private AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader;

		private final int setupId;

		private final Converter< Volatile< T >, VolatileARGBType > converter;

		final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[] {
					new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
					new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
			};
		}

		public ARGBConvertedSource(
				final int setupId,
				final AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader,
				final Converter< Volatile< T >, VolatileARGBType > converter )
		{
			this.setupId = setupId;
			this.loader = loader;
			this.converter = converter;
		}

		final public AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > getLoader()
		{
			return loader;
		}

		@Override
		public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
		{
			return Converters.convert(
					loader.getVolatileImage( t, level ),
					converter,
					new VolatileARGBType() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		/**
		 * TODO Store this in a field
		 */
		@Override
		public int getNumMipmapLevels()
		{
			return loader.getMipmapResolutions().length;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{

			final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
					Views.extendValue( getSource( t, level ), new VolatileARGBType( 0 ) );
			switch ( method )
			{
			case NLINEAR:
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default:
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}

		@Override
		public VolatileARGBType getType()
		{
			return new VolatileARGBType();
		}

		@Override
		public String getName()
		{
			return "1 2 3";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		// TODO: make ARGBType version of this source
		public Source nonVolatile()
		{
			return this;
		}
	}

}
