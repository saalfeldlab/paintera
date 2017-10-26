package bdv.bigcat.viewer.atlas.mode;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.viewer3d.Viewer3DController;
import bdv.bigcat.viewer.viewer3d.marchingCubes.ForegroundCheck;
import bdv.labels.labelset.Label;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanelFX;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
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
public class Render3D extends AbstractStateMode
{

	public static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().getClass() );

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< ViewerPanelFX, MouseAndKeyHandler > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

	private final HashMap< Source< ? >, Function< ?, ForegroundCheck< ? > > > foregroundChecks = new HashMap<>();

	private final HashMap< Source< ? >, FragmentSegmentAssignmentState< ? > > frags = new HashMap<>();

	private final HashMap< Source< ? >, ARGBStream > streams = new HashMap<>();

	private final Viewer3DController v3dControl;

	public Render3D( final Viewer3DController v3dControl )
	{
		super();
		this.v3dControl = v3dControl;
	}

	@Override
	public String getName()
	{
		return "Render 3D";
	}

	public void addSource(
			final Source< ? > source,
			final Source< ? > dataSource,
			final ToIdConverter toIdConverter,
			final Function< ?, ForegroundCheck< ? > > foregroundCheck,
			final FragmentSegmentAssignmentState< ? > frag,
			final ARGBStream stream )
	{

		if ( !this.dataSources.containsKey( source ) )
			this.dataSources.put( source, dataSource );
		if ( !this.toIdConverters.containsKey( source ) )
			this.toIdConverters.put( source, toIdConverter );
		if ( !this.foregroundChecks.containsKey( source ) )
			this.foregroundChecks.put( source, foregroundCheck );
		if ( !this.frags.containsKey( source ) )
			this.frags.put( source, frag );
		if ( !this.streams.containsKey( source ) )
			this.streams.put( source, stream );
	}

	public void removeSource( final Source< ? > source )
	{
		this.dataSources.remove( source );
		this.toIdConverters.remove( source );
	}

	private class RenderNeuron extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final ViewerPanelFX viewer;

		private final boolean append;

		public RenderNeuron( final String name, final ViewerPanelFX viewer, final boolean append )
		{
			super( name );
			this.viewer = viewer;
			this.append = append;
		}

		@Override
		public void click( final int x, final int y )
		{
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
										( FragmentSegmentAssignmentState ) frags.get( spimSource ),
										streams.get( spimSource ),
										append );
							} ).start();
						}
						else
							LOG.warn( "Selected irregular label: {}. Will not render.", selectedId );
					}
				}
			}
		}

	}

	@Override
	protected Consumer< ViewerPanelFX > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();
				final Behaviours behaviours = new Behaviours( inputTriggerConfig );
				final RenderNeuron render = new RenderNeuron( "render neuron", t, false );
				final RenderNeuron append = new RenderNeuron( "render neuron append", t, true );
				behaviours.namedBehaviour( render, "shift button1" );
				behaviours.namedBehaviour( append, "shift button3" );
				final TriggerBehaviourBindings bindings = new TriggerBehaviourBindings();
				behaviours.install( bindings, "render" );
				final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
				mouseAndKeyHandler.setInputMap( bindings.getConcatenatedInputTriggerMap() );
				mouseAndKeyHandler.setBehaviourMap( bindings.getConcatenatedBehaviourMap() );
				this.mouseAndKeyHandlers.put( t, mouseAndKeyHandler );
			}
			t.getDisplay().addHandler( this.mouseAndKeyHandlers.get( t ) );

		};
	}

	@Override
	public Consumer< ViewerPanelFX > onExit()
	{
		return t -> {
			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

}
