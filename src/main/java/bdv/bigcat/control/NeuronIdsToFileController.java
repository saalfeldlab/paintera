package bdv.bigcat.control;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.ui.BrushOverlay;
import bdv.labels.labelset.Label;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
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
public class NeuronIdsToFileController
{
	final protected ViewerPanel viewer;
	final protected RandomAccessibleInterval< LongType > labels;
	final protected RandomAccessible< LongType > extendedLabels;
	final protected AffineTransform3D labelTransform;
	final protected FragmentSegmentAssignment assignment;
	final protected SelectionController selectionController;
	final protected RealPoint labelLocation;
	final protected BrushOverlay brushOverlay;

	final protected int[] labelsH5CellDimensions;

	final int brushNormalAxis;

	protected int brushRadius = 5;

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

	public BrushOverlay getBrushOverlay()
	{
		return brushOverlay;
	}

	/**
	 * Coordinates where mouse dragging started.
	 */
	private int oX, oY;

	public NeuronIdsToFileController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LongType > labels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final SelectionController selectionController,
			final int[] labelsH5CellDimensions,
			final InputTriggerConfig config,
			final int brushNormalAxis )
	{
		this.viewer = viewer;
		this.labels = labels;
		extendedLabels = Views.extendValue( this.labels, new LongType( Label.TRANSPARENT ) );
		this.labelTransform = labelTransform;
		this.assignment = assignment;
		this.selectionController = selectionController;
		this.labelsH5CellDimensions = labelsH5CellDimensions;
		this.brushNormalAxis = brushNormalAxis;
		brushOverlay = new BrushOverlay( viewer );
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "brush" );

		labelLocation = new RealPoint( 3 );
		
		new StoreId("store ids", "Q button1").register();;

//		new Paint( "paint", "SPACE button1" ).register();
//		new Erase( "erase", "SPACE button2", "SPACE button3" ).register();
//		new ChangeBrushRadius( "change brush radius", "SPACE scroll" ).register();
//		new MoveBrush( "move brush", "SPACE" ).register();
	}

	public NeuronIdsToFileController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LongType > labels,
			final AffineTransform3D labelTransform,
			final FragmentSegmentAssignment assignment,
			final SelectionController selectionController,
			final int[] labelsH5CellDimensions,
			final InputTriggerConfig config )
	{
		this( viewer, labels, labelTransform, assignment, selectionController, labelsH5CellDimensions, config, 2 );
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

	private class StoreId extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public StoreId( final String name, final String... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click(int x, int y) {
			// TODO Auto-generated method stub
			synchronized (viewer) {
				setCoordinates(x, y);
				StringBuilder sb = new StringBuilder();
				sb.append(selectionController.getHoverFragmentId());
				double[] resolution = { 4.0, 4.0, 40.0 };
				for ( int d = 0; d < 3; ++d ) {
					sb.append(",").append(labelLocation.getDoublePosition(d)*resolution[d]);
				}
				sb.append("\n");
				String entry = sb.toString();
				System.out.print(entry);
				try {
					String fn = "/home_workaround/haddadaa/ids-c+.csv";
					if ( ! new File(fn).exists())
						new File(fn).createNewFile();
					Files.write( Paths.get(fn), entry.getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

}
