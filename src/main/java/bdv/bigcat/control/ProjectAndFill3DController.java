package bdv.bigcat.control;

import static bdv.bigcat.control.LabelFillController.getBiggestLabel;

import bdv.bigcat.ui.BrushOverlay;
import net.imglib2.*;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.algorithm.fill.Filter;
import net.imglib2.algorithm.fill.FloodFill;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.converter.Converter;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.RandomAccessiblePair;
import net.imglib2.view.Views;

import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;
import bdv.labels.labelset.LabelMultisetType;
import bdv.viewer.ViewerPanel;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import java.awt.*;

/**
 * @author Philipp Hanslovsky &lt;hanslovskyp@janelia.hhmi.org&gt;
 */
public class ProjectAndFill3DController
{

	private final static int CLEANUP_THRESHOLD = ( int ) 1e5;

	// current work around: fill intersect with dummy color, then fill dummy
	// color with initial color
	private final static int DUMMY_PAINT = -2;

	public static final Filter<
            Pair< Pair< LabelMultisetType, LongType >, LongType >,
            Pair< Pair< LabelMultisetType, LongType >, LongType > > LABEL_FILTER =
			( current, reference ) -> current.getA().getB().getIntegerLong() == reference.getA().getB().getIntegerLong();

	public static class LabelFilter< T extends IntegerType< T > >
			implements Filter< Pair< Pair< LabelMultisetType, T >, T >, Pair< Pair< LabelMultisetType, T >, T > >
	{

//        private final long newLabel;

//        public LabelFilter(long newLabel) {
//            this.newLabel = newLabel;
//        }

		@Override
		public boolean accept( Pair< Pair< LabelMultisetType, T >, T > current, Pair< Pair< LabelMultisetType, T >, T > reference )
		{
			long currentPaint = current.getA().getB().getIntegerLong();
			return currentPaint == reference.getA().getB().getIntegerLong();
		}
	}


	final protected ViewerPanel viewer;

	final protected RandomAccessibleInterval< LabelMultisetType > labels;

	final protected RandomAccessibleInterval< LongType > paintedLabels;

	final protected AffineTransform3D labelTransform;

	final protected FragmentSegmentAssignment assignment;

	final protected SelectionController selectionController;

	final protected RealPoint labelLocation;

	final protected Shape shape;

	final protected ArrayImg<BitType, LongArray> ownCanvas;

	private int oX, oY;

	private final BrushOverlay brushOverlay;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();

	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();

	protected final InputTriggerMap blockingTriggerMap = new InputTriggerMap();

	private InputTriggerAdder inputAdder;

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	public ProjectAndFill3DController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labels,
			final RandomAccessibleInterval< LongType > paintedLabels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final SelectionController selectionController,
			final Shape shape,
			final InputTriggerConfig config
	)
	{
		this.viewer = viewer;
		this.labels = labels;
		this.paintedLabels = paintedLabels;
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.selectionController = selectionController;
		this.shape = shape;
		this.inputAdder = config.inputTriggerAdder( inputTriggerMap, "restrict" );

		this.labelLocation = new RealPoint( 3 );

		this.ownCanvas = ArrayImgs.bits( viewer.getWidth(), viewer.getHeight() );

		this.brushOverlay = new BrushOverlay( viewer );

		



//		private final originalInputTriggerMap

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

//	private final class BlockAllActionsBehavior extends SelfRegisteringBehaviour
//	{
//
//	}


	private abstract class AbstractPaintBehavior extends SelfRegisteringBehaviour implements DragBehaviour
	{
		public AbstractPaintBehavior( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		protected void paint( final RealLocalizable coords)
		{
			final HyperSphere< BitType > sphere =
					new HyperSphere<>(
							ownCanvas,
							new Point(
									Math.round( coords.getDoublePosition( 1 ) ),
									Math.round( coords.getDoublePosition( 2 ) ) ),
							2 );
			for ( final BitType t : sphere )
				t.set( true );
		}

		protected void paint( final int x, final int y )
		{
			setCoordinates( x, y );
			paint( labelLocation );
		}

		protected void paint( final int x1, final int y1, final int x2, final int y2 )
		{
			setCoordinates( x1, y1 );
			final double[] p1 = new double[ 3 ];
			final RealPoint rp1 = RealPoint.wrap( p1 );
			labelLocation.localize( p1 );

			setCoordinates( x2, y2 );
			final double[] d = new double[ 3 ];
			labelLocation.localize( d );

			LinAlgHelpers.subtract( d, p1, d );

			final double l = LinAlgHelpers.length( d );
			LinAlgHelpers.normalize( d );

			for ( int i = 1; i < l; ++i )
			{
				LinAlgHelpers.add( p1, d, p1 );
				paint( rp1 );
			}
			paint( labelLocation );
		}

//		abstract protected long getValue();

		@Override
		public void init( final int x, final int y )
		{
			synchronized ( this )
			{
				oX = x;
				oY = y;
			}

			paint( x, y );

			viewer.requestRepaint();

			// System.out.println( getName() + " drag start (" + oX + ", " + oY + ")" );
		}

		@Override
		public void drag( final int x, final int y )
		{
			brushOverlay.setPosition( x, y );

			paint( oX, oY, x, y );

			synchronized ( this )
			{
				oX = x;
				oY = y;
			}

			viewer.requestRepaint();

			// System.out.println( getName() + " drag by (" + dX + ", " + dY + ")" );
		}

		@Override
		public void end( final int x, final int y )
		{}
	}

	private class Paint extends AbstractPaintBehavior
	{
		public Paint( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

//		@Override
//		protected long getValue()
//		{
//			return selectionController.getActiveFragmentId();
//		}
	}

	private class Intersect extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public Intersect( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			synchronized ( viewer )
			{

			}
		}
	}


}
