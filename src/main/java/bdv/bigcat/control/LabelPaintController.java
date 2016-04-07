package bdv.bigcat.control;

import static bdv.labels.labelset.PairVolatileLabelMultisetLongARGBConverter.TRANSPARENT_LABEL;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.FragmentSegmentAssignment;
import bdv.bigcat.MergeController;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.viewer.ViewerPanel;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.view.Views;

/**
 * A {@link TransformEventHandler} that changes an {@link AffineTransform3D}
 * through a set of {@link Behaviour}s.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class LabelPaintController
{
	final protected ViewerPanel viewer;
	final protected RandomAccessibleInterval< LongType > labels;
	final protected RandomAccessible< LongType > extendedLabels;
	final protected AffineTransform3D labelTransform;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	final protected MergeController mergeController;
	final protected RealPoint labelLocation;
	
	protected int brushRadius = 5;
	
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputMap = new InputTriggerMap();

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}
	
	public InputTriggerMap getInputTriggerMap()
	{
		return inputMap;
	}
	
	private final InputTriggerAdder inputAdder;

	/**
	 * Coordinates where mouse dragging started.
	 */
	private double oX, oY;

	public LabelPaintController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LongType > labels,
			final AffineTransform3D labelTransform,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment,
			final MergeController mergeController,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		extendedLabels = Views.extendValue( this.labels, new LongType( TRANSPARENT_LABEL ) );
		this.labelTransform = labelTransform;
		this.colorStream = colorStream;
		this.assignment = assignment;
		this.mergeController = mergeController;
		inputAdder = config.inputTriggerAdder( inputMap, "bigcat" );

		labelLocation = new RealPoint( 3 );
		
		new Paint( "paint", "SPACE button1" ).register();
		new Erase( "erase", "SPACE button2", "SPACE button3" ).register();
		new ChangeBrushRadius( "change brush radius", "SPACE scroll", "SPACE scroll" ).register();
	}
	
	private void setCoordinates( final int x, final int y )
	{
		labelLocation.setPosition( x, 0 );
		labelLocation.setPosition( y, 1 );
		labelLocation.setPosition( 0, 2 );

		viewer.displayToGlobalCoordinates( labelLocation );
		
		labelTransform.applyInverse( labelLocation, labelLocation );
	}
	
	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;

		protected String getName()
		{
			return name;
		}
		
		public SelfRegisteringBehaviour( final String name, final String ... defaultTriggers )
		{
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public void register()
		{
			behaviourMap.put( name, this );
			inputAdder.put( name, defaultTriggers );
		}
	}
	
	private abstract class AbstractPaintBehavior extends SelfRegisteringBehaviour implements DragBehaviour
	{
		public AbstractPaintBehavior( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		protected void paint( final int x, final int y, final long value )
		{
			setCoordinates( x, y );
			final HyperSphere< LongType > sphere =
					new HyperSphere<>(
							Views.hyperSlice( labels, 0, Math.round( labelLocation.getDoublePosition( 0 ) ) ),
							new Point( Math.round( labelLocation.getDoublePosition( 1 ) ), Math.round( labelLocation.getDoublePosition( 2 ) ) ),
							brushRadius );
			for ( final LongType t : sphere )
				t.set( value );
		}
		
		@Override
		public void init( final int x, final int y )
		{
			oX = x;
			oY = y;
			
//			System.out.println( getName() + " drag start (" + oX + ", " + oY + ")" );
		}

		@Override
		public void drag( final int x, final int y )
		{
			final double dX;
			final double dY;
			dX = oX - x;
			dY = oY - y;
			
//			System.out.println( getName() + " drag by (" + dX + ", " + dY + ")" );
		}

		@Override
		public void end( final int x, final int y )
		{}	
	}

	private class Paint extends AbstractPaintBehavior
	{
		public Paint( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void init( final int x, final int y )
		{
			synchronized ( labelLocation )
			{
				super.init( x, y );
				paint( x, y, mergeController.getActiveFragmentId() );
			}
			
			viewer.requestRepaint();
		}
		
		@Override
		public void drag( final int x, final int y )
		{
			synchronized ( labelLocation )
			{
				super.drag( x, y );
				paint( x, y, mergeController.getActiveFragmentId() );
			}
			
			viewer.requestRepaint();
		}
	}

	private class Erase extends AbstractPaintBehavior
	{
		public Erase( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void init( final int x, final int y )
		{
			synchronized ( labelLocation )
			{
				super.init( x, y );
				paint( x, y, TRANSPARENT_LABEL );
			}
			
			viewer.requestRepaint();
		}
		
		@Override
		public void drag( final int x, final int y )
		{
			synchronized ( labelLocation )
			{
				super.drag( x, y );
				paint( x, y, TRANSPARENT_LABEL );
			}
			
			viewer.requestRepaint();
		}
	}
	
	private class ChangeBrushRadius extends SelfRegisteringBehaviour implements ScrollBehaviour
	{
		public ChangeBrushRadius( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void scroll( double wheelRotation, boolean isHorizontal, int x, int y )
		{
			if ( !isHorizontal )
			{
				if ( wheelRotation < 0 )
					brushRadius += 1;
				else if ( wheelRotation > 0 )
					brushRadius = Math.max( 0, brushRadius - 1 );
				
				System.out.println( "changed brush radius to " + brushRadius );
			}
		}
	}
}
