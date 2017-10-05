package bdv.bigcat.viewer.atlas.mode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.viewer3d.Viewer3DController;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset.Entry;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;

public class Render3D extends AbstractStateMode
{

	private final HashMap< Source< ? >, Source< ? > > dataSources = new HashMap<>();

	private final HashMap< ViewerPanel, MouseAndKeyHandler > mouseAndKeyHandlers = new HashMap<>();

	private final HashMap< Source< ? >, ToIdConverter > toIdConverters = new HashMap<>();

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

	public void addSource( final Source< ? > source, final Source< ? > dataSources, final ToIdConverter toIdConverter )
	{

		if ( !this.dataSources.containsKey( source ) )
			this.dataSources.put( source, dataSources );
		if ( !this.toIdConverters.containsKey( source ) )
			this.toIdConverters.put( source, toIdConverter );
	}

	public void removeSource( final Source< ? > source )
	{
		this.dataSources.remove( source );
		this.toIdConverters.remove( source );
	}

	private class RenderNeuron extends AbstractNamedBehaviour implements ClickBehaviour
	{

		private final ViewerPanel viewer;

		public RenderNeuron( final String name, final ViewerPanel viewer )
		{
			super( name );
			this.viewer = viewer;
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
					if ( dataSource != null && dataSource.getType() instanceof LabelMultisetType )
					{
						final AffineTransform3D viewerTransform = new AffineTransform3D();
						state.getViewerTransform( viewerTransform );
						final int bestMipMapLevel = state.getBestMipMapLevel( viewerTransform, sourceIndex );

						final double[] worldCoordinate = new double[] { x, y, 0 };
						viewerTransform.applyInverse( worldCoordinate, worldCoordinate );
						final long[] worldCoordinateLong = Arrays.stream( worldCoordinate ).mapToLong( d -> ( long ) d ).toArray();

						final int numVolumes = dataSource.getNumMipmapLevels();
						final RandomAccessibleInterval< LabelMultisetType >[] volumes = new RandomAccessibleInterval[ numVolumes ];
						final AffineTransform3D[] transforms = new AffineTransform3D[ numVolumes ];

						for ( int i = 0; i < numVolumes; ++i )
						{
							volumes[ i ] = ( RandomAccessibleInterval< LabelMultisetType > ) dataSource.getSource( numVolumes - 1 - i, 0 );
							final AffineTransform3D tf = new AffineTransform3D();
							dataSource.getSourceTransform( 0, numVolumes - 1 - i, tf );
							transforms[ i ] = tf;
						}

						transforms[ bestMipMapLevel ].applyInverse( worldCoordinate, worldCoordinate );
						final RealRandomAccess< ? > rra = dataSource.getInterpolatedSource( 0, bestMipMapLevel, Interpolation.NEARESTNEIGHBOR ).realRandomAccess();
						rra.setPosition( worldCoordinate );

						final long selectedId = toIdConverters.get( spimSource ).biggestFragment( rra.get() );

						if ( Label.regular( selectedId ) )
						{

//						Viewer3DController.renderAtSelectionMultiset( volumes, transforms, Point.wrap( worldCoordinateLong ), toIdConverters.get( spimSource ).biggestFragment( rra.get() ) );
							final int[] partitionSize = { 64, 64, 10 };
							final int[] cubeSize = { 1, 1, 1 };

							final ToIntFunction< LabelMultisetType > isForeground = multiset -> {
								long argMaxLabel = Label.INVALID;
								long argMaxCount = Integer.MIN_VALUE;
								for ( final Entry< Label > entry : multiset.entrySet() )
								{
									final int count = entry.getCount();
									if ( count > argMaxCount )
									{
										argMaxLabel = entry.getElement().id();
										argMaxCount = count;
									}
								}
								return argMaxLabel == selectedId ? 1 : 0;
							};
//							v3dControl.renderAtSelection(
//									volumes,
//									transforms,
//									Point.wrap( Arrays.stream( worldCoordinate ).mapToLong( d -> ( long ) d ).toArray() ),
//									isForeground,
//									new LabelMultisetType(),
//									partitionSize,
//									cubeSize );
							new Thread( () -> {
								v3dControl.generateMesh(
										volumes[ 0 ],
										Point.wrap( Arrays.stream( worldCoordinate ).mapToLong( d -> ( long ) d ).toArray() ),
										partitionSize,
										cubeSize,
										isForeground,
										new LabelMultisetType() );
							} ).start();
						}
					}
				}
			}
		}

	}

	@Override
	protected Consumer< ViewerPanel > getOnEnter()
	{
		return t -> {
			if ( !this.mouseAndKeyHandlers.containsKey( t ) )
			{
				System.out.println( "Entering for merger!" );
				final InputTriggerConfig inputTriggerConfig = new InputTriggerConfig();
				final Behaviours behaviours = new Behaviours( inputTriggerConfig );
				final RenderNeuron render = new RenderNeuron( "render neuron", t );
				behaviours.namedBehaviour( render, "button1" );
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
	public Consumer< ViewerPanel > onExit()
	{
		return t -> {
			t.getDisplay().removeHandler( this.mouseAndKeyHandlers.get( t ) );
		};
	}

}
