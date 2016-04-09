package bdv.bigcat.control;

import static bdv.labels.labelset.PairVolatileLabelMultisetLongARGBConverter.TRANSPARENT_LABEL;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.ScrollBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.FragmentSegmentAssignment;
import bdv.bigcat.MergeController;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.bigcat.ui.BrushOverlay;
import bdv.img.h5.H5Utils;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
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
	final protected BrushOverlay brushOverlay;
	
	final protected String labelsH5Path;
	final protected String labelsH5Dataset;
	final protected int[] labelsH5CellDimensions;
	
	protected int brushRadius = 5;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder( ksActionMap );
	private final KeyStrokeAdder ksKeyStrokeAdder;
		
	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}
	
	public BrushOverlay getBrushOverlay()
	{
		return brushOverlay;
	}

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
			final String labelsH5Path,
			final String labelsH5Dataset,
			final int[] labelsH5CellDimensions,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings )
	{
		this.viewer = viewer;
		this.labels = labels;
		extendedLabels = Views.extendValue( this.labels, new LongType( TRANSPARENT_LABEL ) );
		this.labelTransform = labelTransform;
		this.colorStream = colorStream;
		this.assignment = assignment;
		this.mergeController = mergeController;
		this.labelsH5Path = labelsH5Path;
		this.labelsH5Dataset = labelsH5Dataset;
		this.labelsH5CellDimensions = labelsH5CellDimensions;
		brushOverlay = new BrushOverlay( viewer );
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "paint" );
		ksKeyStrokeAdder = config.keyStrokeAdder( ksInputMap, "paint" );

		labelLocation = new RealPoint( 3 );

		new Paint( "paint", "SPACE button1" ).register();
		new Erase( "erase", "SPACE button2", "SPACE button3" ).register();
		new ChangeBrushRadius( "change brush radius", "SPACE scroll", "SPACE scroll" ).register();
		new MoveBrush( "move brush", "SPACE" ).register();
		new SavePaintedLabels("save painted labels", "S").register();
		
		inputActionBindings.addActionMap( "paint", ksActionMap );
		inputActionBindings.addInputMap( "paint", ksInputMap );
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
	
	private abstract class SelfRegisteringAction extends AbstractNamedAction
	{
		private final String[] defaultTriggers;
		
		public SelfRegisteringAction( final String name, final String ... defaultTriggers )
		{
			super( name );
			this.defaultTriggers = defaultTriggers;
		}

		public void register()
		{
			ksActionAdder.put( this );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
		}
	}

	private abstract class AbstractPaintBehavior extends SelfRegisteringBehaviour implements DragBehaviour
	{
		public AbstractPaintBehavior( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		protected void paint( final int x, final int y, final long value )
		{
			setCoordinates( x, y );
			final HyperSphere< LongType > sphere =
					new HyperSphere<>(
							Views.hyperSlice( labels, 0, Math.round( labelLocation.getDoublePosition( 0 ) ) ),
							new Point(
									Math.round( labelLocation.getDoublePosition( 1 ) ),
									Math.round( labelLocation.getDoublePosition( 2 ) ) ),
							brushRadius );
			for ( final LongType t : sphere )
				t.set( value );
		}

		@Override
		public void init( final int x, final int y )
		{
			oX = x;
			oY = y;

			// System.out.println( getName() + " drag start (" + oX + ", " + oY + ")" );
		}

		@Override
		public void drag( final int x, final int y )
		{
			final double dX;
			final double dY;
			
			dX = oX - x;
			dY = oY - y;
			
			brushOverlay.setPosition( x, y );

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
		public Erase( final String name, final String... defaultTriggers )
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
		public ChangeBrushRadius( final String name, final String... defaultTriggers )
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

				brushOverlay.setRadius( brushRadius );
				// TODO request only overlays to repaint
				viewer.getDisplay().repaint();
			}
		}
	}

	private class MoveBrush extends SelfRegisteringBehaviour implements DragBehaviour
	{
		public MoveBrush( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void init( int x, int y )
		{
			brushOverlay.setPosition( x, y );
			brushOverlay.setVisible( true );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.CROSSHAIR_CURSOR ) );
			viewer.getDisplay().repaint();
		}

		@Override
		public void drag( int x, int y )
		{
			brushOverlay.setPosition( x, y );
		}

		@Override
		public void end( int x, int y )
		{
			brushOverlay.setVisible( false );
			// TODO request only overlays to repaint
			viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			viewer.getDisplay().repaint();

		}
	}
	
	private class SavePaintedLabels extends SelfRegisteringAction
	{
		public SavePaintedLabels( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			System.out.println( "Saving painted labels into " + labelsH5Path );
			
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				H5Utils.saveUnsignedLong( labels, new File( labelsH5Path ), labelsH5Dataset, labelsH5CellDimensions );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
		}
	}
}
