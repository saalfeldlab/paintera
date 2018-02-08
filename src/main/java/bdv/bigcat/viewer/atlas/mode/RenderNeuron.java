package bdv.bigcat.viewer.atlas.mode;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.source.SourceInfo;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.stream.ARGBStream;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import bdv.labels.labelset.Label;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import javafx.scene.input.MouseEvent;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.view.Views;

/**
 *
 *
 * @author Vanessa Leite
 * @author Philipp Hanslovsky
 */
public class RenderNeuron
{
	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().getClass() );

	private final ViewerPanelFX viewer;

	private final boolean append;

	private final SourceInfo sourceInfo;

	private final Viewer3DControllerFX v3dControl;

	private final GlobalTransformManager transformManager;

	private final Mode mode;

	public RenderNeuron(
			final ViewerPanelFX viewer,
			final boolean append,
			final SourceInfo sourceInfo,
			final Viewer3DControllerFX v3dControl,
			final GlobalTransformManager transformManager,
			final Mode mode )
	{
		super();
		this.viewer = viewer;
		this.append = append;
		this.sourceInfo = sourceInfo;
		this.v3dControl = v3dControl;
		this.transformManager = transformManager;
		this.mode = mode;
	}

	public void click( final MouseEvent e )
	{
		final double x = e.getX();
		final double y = e.getY();
		synchronized ( viewer )
		{
			final ViewerState state = viewer.getState();
			final Source< ? > source = sourceInfo.currentSourceProperty().get();
			if ( source != null && sourceInfo.getState( source ).visibleProperty().get() )
				if ( source instanceof DataSource< ?, ? > )
				{
					final int sourceIndex = sourceInfo.trackVisibleSources().indexOf( source );
					final DataSource< ?, ? > dataSource = sourceInfo.getState( source ).dataSourceProperty().get();
					final Optional< Function< ?, Converter< ?, BoolType > > > toBoolConverter = sourceInfo.toBoolConverter( source );
					final Optional< ToIdConverter > idConverter = sourceInfo.toIdConverter( source );
					final Optional< SelectedIds > selectedIds = sourceInfo.selectedIds( source, mode );
					final Optional< ? extends FragmentSegmentAssignmentState< ? > > assignment = sourceInfo.assignment( source );
					final Optional< ARGBStream > stream = sourceInfo.stream( source, mode );
					if ( toBoolConverter.isPresent() && idConverter.isPresent() && selectedIds.isPresent() && assignment.isPresent() && stream.isPresent() )
					{
						final AffineTransform3D viewerTransform = new AffineTransform3D();
						state.getViewerTransform( viewerTransform );
						final int bestMipMapLevel = state.getBestMipMapLevel( viewerTransform, sourceIndex );

						final double[] worldCoordinate = new double[] { x, y, 0 };
						viewerTransform.applyInverse( worldCoordinate, worldCoordinate );
						final long[] worldCoordinateLong = Arrays.stream( worldCoordinate ).mapToLong( d -> ( long ) d ).toArray();

						final int numVolumes = dataSource.getNumMipmapLevels();
						final RandomAccessible[] volumes = new RandomAccessible[ numVolumes ];
						final Interval[] intervals = new Interval[ numVolumes ];
						final AffineTransform3D[] transforms = new AffineTransform3D[ numVolumes ];

						for ( int i = 0; i < numVolumes; ++i )
						{
							volumes[ i ] = Views.raster( dataSource.getInterpolatedDataSource( 0, numVolumes - 1 - i, Interpolation.NEARESTNEIGHBOR ) );
							intervals[ i ] = dataSource.getSource( 0, numVolumes - 1 - i );
							final AffineTransform3D tf = new AffineTransform3D();
							dataSource.getSourceTransform( 0, numVolumes - 1 - i, tf );
							transforms[ i ] = tf;
						}

						final double[] imageCoordinate = new double[ worldCoordinate.length ];
						transforms[ 0 ].applyInverse( imageCoordinate, worldCoordinate );
						final RealRandomAccess< ? > rra = dataSource.getInterpolatedDataSource( 0, bestMipMapLevel, Interpolation.NEARESTNEIGHBOR ).realRandomAccess();
						rra.setPosition( imageCoordinate );

						final long selectedId = idConverter.get().biggestFragment( rra.get() );

						if ( Label.regular( selectedId ) )
						{
							final SelectedIds selIds = selectedIds.get();

							if ( selIds.isActive( selectedId ) )
							{
								final double[] a = transforms[ 0 ].getRowPackedCopy();
								final double scaleX = Math.sqrt(
										a[ 0 ] * a[ 0 ] +
												a[ 4 ] * a[ 4 ] +
												a[ 8 ] * a[ 8 ] );
								final double scaleY = Math.sqrt(
										a[ 1 ] * a[ 1 ] +
												a[ 5 ] * a[ 5 ] +
												a[ 9 ] * a[ 9 ] );
								final double scaleZ = Math.sqrt(
										a[ 2 ] * a[ 2 ] +
												a[ 6 ] * a[ 6 ] +
												a[ 10 ] * a[ 10 ] );

								final double scaleMax = Math.max( Math.max( scaleX, scaleY ), scaleZ );
								final int stepSizeX = ( int ) Math.round( scaleMax / scaleX );
								final int stepSizeY = ( int ) Math.round( scaleMax / scaleY );
								final int stepSizeZ = ( int ) Math.round( scaleMax / scaleZ );
								final int maxBlockScale = ( int )Math.ceil( 256.0 / Math.max( Math.max( stepSizeX, stepSizeY ), stepSizeZ ) );

								final int[] partitionSize = {
										maxBlockScale * stepSizeX,
										maxBlockScale * stepSizeY,
										maxBlockScale * stepSizeZ };

								final int[] cubeSize = { stepSizeX, stepSizeY, stepSizeZ };

								final Function getForegroundCheck = toBoolConverter.get();
								new Thread( () -> {
									v3dControl.generateMesh(
											volumes[ 0 ],
											intervals[ 0 ],
											transforms[ 0 ],
											new RealPoint( worldCoordinate ),
											partitionSize,
											cubeSize,
											getForegroundCheck,
											selectedId,
											( FragmentSegmentAssignmentState ) assignment.get(),
											stream.get(),
											append,
											selIds,
											transformManager );
								} ).start();
							}
							else
								new Thread( () -> v3dControl.removeMesh( selectedId ) ).start();
						}
						else
							LOG.warn( "Selected irregular label: {}. Will not render.", selectedId );
					}
				}
		}
	}

}
