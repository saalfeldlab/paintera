package bdv.bigcat.control;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.FragmentSegmentAssignment;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.labels.labelset.VolatileLabelMultisetType;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformEventHandler;

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
	final protected RealRandomAccessible< VolatileLabelMultisetType > labels;
	final protected RealRandomAccess< VolatileLabelMultisetType > labelAccess;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected FragmentSegmentAssignment assignment;
	protected long activeId = 0;

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
			final RealRandomAccessible< VolatileLabelMultisetType > labels,
			final AbstractSaturatedARGBStream colorStream,
			final FragmentSegmentAssignment assignment,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		labelAccess = labels.realRandomAccess();
		this.colorStream = colorStream;
		this.assignment = assignment;
		inputAdder = config.inputTriggerAdder( inputMap, "bigcat" );

		new Paint( "paint", "SPACE button1" ).register();
		new Erase( "erase", "SPACE button2", "SPACE button3" ).register();
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
		
		@Override
		public void init( final int x, final int y )
		{
			synchronized ( viewer )
			{
				oX = x;
				oY = y;
			}
			
			System.out.println( getName() + " drag start (" + oX + ", " + oY + ")" );
		}

		@Override
		public void drag( final int x, final int y )
		{
			final double dX;
			final double dY;
			synchronized ( viewer )
			{
				dX = oX - x;
				dY = oY - y;
			}

			System.out.println( getName() + " drag by (" + dX + ", " + dY + ")" );
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
	}

	private class Erase extends AbstractPaintBehavior
	{
		public Erase( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
	}
}
