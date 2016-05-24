package bdv.bigcat.control;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.labels.labelset.Multiset;
import bdv.viewer.ViewerPanel;
import net.imglib2.*;
import net.imglib2.Point;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import java.awt.Cursor;

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

	final protected SelectionController selectionController;

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
			final SelectionController selectionController,
			final Shape shape,
			final InputTriggerConfig config )
	{
		this.viewer = viewer;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.selectionController = selectionController;
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
				System.out.println( "Filling " + labelLocation + " with " + selectionController.getActiveFragmentId() );

				final Point p = new Point(
						Math.round( labelLocation.getDoublePosition( 0 ) ),
						Math.round( labelLocation.getDoublePosition( 1 ) ),
						Math.round( labelLocation.getDoublePosition( 2 ) ) );

				RandomAccess< LongType > paintAccess = paintedLabels.randomAccess();
				paintAccess.setPosition( p );
				long seedPaint = paintAccess.get().getIntegerLong();
				long seedFragmentLabel = getBiggestLabel( labels, p );

				final long t0 = System.currentTimeMillis();
				FloodFill.fill(
						Views.extendValue( labels, new LabelMultisetType() ),
						Views.extendValue( paintedLabels, new LongType( Label.TRANSPARENT ) ),
						p,
						new LabelMultisetType(),
						new LongType( selectionController.getActiveFragmentId() ),
						new DiamondShape( 1 ),
						new SegmentAndPaintFilter1(
								seedPaint,
								seedFragmentLabel,
								assignment ) );
				final long t1 = System.currentTimeMillis();
				System.out.println( "Filling took " + ( t1 - t0 ) + " ms" );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
				viewer.requestRepaint();
			}
		}
	}

	public static class SegmentAndPaintFilter1 implements Filter< Pair< LabelMultisetType, LongType >, Pair< LabelMultisetType, LongType > >
	{
		private final long comparison;

		private final long[] fragmentsContainedInSeedSegment;

		public SegmentAndPaintFilter1( long seedPaint, long seedFragmentLabel, FragmentSegmentAssignment assignment )
		{
			this.comparison = seedPaint == Label.TRANSPARENT ? seedFragmentLabel : seedPaint;
			this.fragmentsContainedInSeedSegment = assignment.getFragments( assignment.getSegment( comparison ) );
		}

		@Override
		public boolean accept( Pair< LabelMultisetType, LongType > current, Pair< LabelMultisetType, LongType > reference )
		{

			final LabelMultisetType currentLabelSet = current.getA();
			final long currentPaint = current.getB().getIntegerLong();

			if ( currentPaint != Label.TRANSPARENT )
				return currentPaint == comparison && currentPaint != reference.getB().getIntegerLong();

			else
			{
				for ( long fragment : this.fragmentsContainedInSeedSegment )
				{
					if ( currentLabelSet.contains( fragment ) )
						return true;
				}
			}

			return false;
		}
	}

	public static class FragmentFilter implements Filter< Pair< LabelMultisetType, LongType >, Pair< LabelMultisetType, LongType > >
	{

		private final long seedLabel;

		public FragmentFilter( long seedLabel )
		{
			this.seedLabel = seedLabel;
		}

		@Override
		public boolean accept( Pair< LabelMultisetType, LongType > current, Pair< LabelMultisetType, LongType > reference )
		{
			return ( current.getB().getIntegerLong() != reference.getB().getIntegerLong() ) && ( current.getA().contains( seedLabel ) );
		}

	}

	public static long getBiggestLabel( RandomAccessible< LabelMultisetType > accessible, Localizable position )
	{
		RandomAccess< LabelMultisetType > access = accessible.randomAccess();
		access.setPosition( position );
		return getBiggestLabel( access.get() );
	}

	public static long getBiggestLabel( LabelMultisetType t )
	{
		int maxCount = Integer.MIN_VALUE;
		long maxLabel = -1;
		for ( Multiset.Entry< Label > e : t.entrySet() )
		{
			int c = e.getCount();
			if ( c > maxCount )
			{
				maxLabel = e.getElement().id();
				maxCount = c;
			}
		}
		return maxLabel;
	}

}
