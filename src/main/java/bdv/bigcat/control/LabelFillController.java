package bdv.bigcat.control;

import static bdv.labels.labelset.PairVolatileLabelMultisetLongARGBConverter.TRANSPARENT_LABEL;

import java.awt.Cursor;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.FragmentSegmentAssignment;
import bdv.labels.labelset.LabelMultisetFill;
import bdv.labels.labelset.LabelMultisetType;
import bdv.viewer.ViewerPanel;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

/**
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class LabelFillController
{
	final protected ViewerPanel viewer;
	final protected RandomAccessibleInterval< LabelMultisetType > labels;
	final protected RandomAccessibleInterval< LongType > paintedLabels;
	final protected AffineTransform3D labelTransform;
	final protected FragmentSegmentAssignment assignment;
	final protected MergeController mergeController;
	final protected RealPoint labelLocation;
	final protected Shape shape;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	public LabelFillController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labels,
			final RandomAccessibleInterval< LongType > paintedLabels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final MergeController mergeController,
			final Shape shape,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.mergeController = mergeController;
		this.shape = shape;
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "fill" );

		labelLocation = new RealPoint( 3 );

		new Fill( "fill", "M button1" ).register();
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

		public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
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

	private class Fill extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public Fill( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				setCoordinates( x, y );
				System.out.println( "Filling " + labelLocation + " with " + mergeController.getActiveFragmentId() );
				final Point p = new Point(
						( long )Math.round( labelLocation.getDoublePosition( 0 ) ),
						( long )Math.round( labelLocation.getDoublePosition( 1 ) ),
						( long )Math.round( labelLocation.getDoublePosition( 2 ) ) );
				long t0 = System.currentTimeMillis();
				LabelMultisetFill.fill(
						Views.extendValue( labels, new LabelMultisetType() ),
						Views.extendValue( paintedLabels, new LongType( TRANSPARENT_LABEL ) ),
						p,
						shape,
						new LabelMultisetFill.IntegerTypeFillPolicySegmentsConsiderBackgroundAndCanvas2.Factory<>(
								mergeController.getActiveFragmentId(),
								assignment
						) );
				long t1 = System.currentTimeMillis();
				System.out.println( "Filling took " + (t1-t0) + " ms" );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
				viewer.requestRepaint();
			}
		}
	}
}
