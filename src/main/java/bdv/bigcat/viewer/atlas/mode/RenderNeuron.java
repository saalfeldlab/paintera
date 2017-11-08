package bdv.bigcat.viewer.atlas.mode;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.GlobalTransformManager;
import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.viewer3d.Viewer3DControllerFX;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.labels.labelset.Label;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import javafx.scene.input.MouseEvent;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
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

	private final HashMap< Source< ? >, Source< ? > > dataSources;

	private final HashMap< Source< ? >, Function< ?, ForegroundCheck< ? > > > foregroundChecks;

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters;

	private final HashMap< Source< ? >, SelectedIds > selectedIds;

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState > frags;

	private final Viewer3DControllerFX v3dControl;

	private final HashMap< Source< ? >, ARGBStream > streams;

	private final GlobalTransformManager transformManager;

	public RenderNeuron(
			final ViewerPanelFX viewer,
			final boolean append, final HashMap< Source< ? >, Source< ? > > dataSources,
			final HashMap< Source< ? >, Function< ?, ForegroundCheck< ? > > > foregroundChecks,
			final HashMap< Source< ? >, ToIdConverter > toIdConverters,
			final HashMap< Source< ? >, SelectedIds > selectedIds,
			final HashMap< Source< ? >, FragmentSegmentAssignmentState > frags,
			final Viewer3DControllerFX v3dControl,
			final HashMap< Source< ? >, ARGBStream > streams,
			final GlobalTransformManager transformManager )
	{
		super();
		this.viewer = viewer;
		this.append = append;
		this.dataSources = dataSources;
		this.foregroundChecks = foregroundChecks;
		this.toIdConverters = toIdConverters;
		this.selectedIds = selectedIds;
		this.frags = frags;
		this.v3dControl = v3dControl;
		this.streams = streams;
		this.transformManager = transformManager;
	}

	public void click( final MouseEvent e )
	{
		final double x = e.getX();
		final double y = e.getY();
		synchronized ( viewer )
		{
			final ViewerState state = viewer.getState();
			final List< SourceState< ? > > sources = state.getSources();
			final int sourceIndex = state.getCurrentSource();
			if ( sourceIndex > 0 && sources.size() > sourceIndex )
			{
				final SourceState< ? > source = sources.get( sourceIndex );
				final Source< ? > spimSource = source.getSpimSource();
				final Source< ? > dataSource = dataSources.get( spimSource );
				if ( dataSource != null && foregroundChecks.containsKey( spimSource ) )
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
						volumes[ i ] = Views.raster( dataSource.getInterpolatedSource( 0, numVolumes - 1 - i, Interpolation.NEARESTNEIGHBOR ) );
						intervals[ i ] = dataSource.getSource( 0, numVolumes - 1 - i );
						final AffineTransform3D tf = new AffineTransform3D();
						dataSource.getSourceTransform( 0, numVolumes - 1 - i, tf );
						transforms[ i ] = tf;
					}

					final double[] imageCoordinate = new double[ worldCoordinate.length ];
					transforms[ 0 ].applyInverse( imageCoordinate, worldCoordinate );
					final RealRandomAccess< ? > rra = dataSource.getInterpolatedSource( 0, bestMipMapLevel, Interpolation.NEARESTNEIGHBOR ).realRandomAccess();
					rra.setPosition( imageCoordinate );

					final long selectedId = toIdConverters.get( spimSource ).biggestFragment( rra.get() );

					if ( Label.regular( selectedId ) )
					{
						final SelectedIds selIds = selectedIds.get( spimSource );

						if ( selIds.isActive( selectedId ) )
						{

							final int[] partitionSize = { 60, 60, 10 };
							final int[] cubeSize = { 10, 10, 1 };

							final Function getForegroundCheck = foregroundChecks.get( spimSource );
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
										frags.get( spimSource ),
										streams.get( spimSource ),
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
