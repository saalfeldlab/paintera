package bdv.bigcat.control;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.IdService;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.LongType;

/**
 * Persist fragment segment assignments, painted labels, viewer state, and
 * flattened labels.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class LabelPersistenceController
{
	final protected ViewerPanel viewer;
	final protected RandomAccessibleInterval< LabelMultisetType > labelMultisetSource;
	final protected RandomAccessibleInterval< LongType > labelSource;
	final protected double[] labelResolution;
	final protected FragmentSegmentAssignment assignment;
	final protected IdService idService;

	final protected String h5Path;
	final protected String paintedLabelsDataset;
	final protected String mergedLabelsDataset;
	final protected int[] labelsCellDimensions;
	final protected String assignmentDataset;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public LabelPersistenceController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labelMultisetSource,
			final RandomAccessibleInterval< LongType > labelSource,
			final double[] labelResolution,
			final FragmentSegmentAssignment assignment,
			final IdService idService,
			final String h5Path,
			final String paintedLabelsDataset,
			final String mergedLabelsDataset,
			final int[] labelsH5CellDimensions,
			final String assignmentDataset,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings )
	{
		this.viewer = viewer;
		this.labelMultisetSource = labelMultisetSource;
		this.labelSource = labelSource;
		this.labelResolution = labelResolution;
		this.assignment = assignment;
		this.idService = idService;
		this.h5Path = h5Path;
		this.paintedLabelsDataset = paintedLabelsDataset;
		this.mergedLabelsDataset = mergedLabelsDataset;
		this.labelsCellDimensions = labelsH5CellDimensions;
		this.assignmentDataset = assignmentDataset;
		ksKeyStrokeAdder = config.keyStrokeAdder( ksInputMap, "persistence" );

		new SaveFragmentSegmentAssignmentAndPaintedLabels( "save fragment segment assignment and painted labels", "ctrl S" ).register();
		new SaveAssignedMergedLabels( "save assigned merged labels", "ctrl shift S" ).register();

		inputActionBindings.addActionMap( "persistence", ksActionMap );
		inputActionBindings.addInputMap( "persistence", ksInputMap );
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
			put( ksActionMap );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
		}
	}

	public void saveNextId()
	{
		System.out.println( "Saving next id " + h5Path + ":/next_id" );
		H5Utils.saveUint64Attribute(
				idService.next(),
				h5Path,
				"/",
				"next_id" );
	}

	public void saveFragmentSegmentAssignment()
	{
		System.out.println( "Saving fragment-segment assignments " + h5Path + ":" + assignmentDataset );
		H5Utils.saveLongLongLut(
				assignment.getLut(),
				h5Path,
				assignmentDataset,
				1024 );
	}

	public void savePaintedLabels()
	{
		System.out.println( "Saving painted labels into " + h5Path + ":" + paintedLabelsDataset );

		final File file = new File( h5Path );
		H5Utils.saveUnsignedLong(
				labelSource,
				file,
				paintedLabelsDataset,
				labelsCellDimensions );
		H5Utils.saveDoubleArrayAttribute(
				new double[]{labelResolution[2], labelResolution[1], labelResolution[0]},
				file,
				paintedLabelsDataset,
				"resolution");
	}

	public void saveMergedLabels()
	{
		System.out.println( "Saving merged labels into " + h5Path + ":" + mergedLabelsDataset  );

		final File file = new File( h5Path );
		H5Utils.saveSingleElementLabelMultisetLongPair(
				labelMultisetSource,
				labelSource,
				labelSource,
				file,
				mergedLabelsDataset,
				labelsCellDimensions );
		H5Utils.saveDoubleArrayAttribute(
				new double[]{labelResolution[2], labelResolution[1], labelResolution[0]},
				file,
				mergedLabelsDataset,
				"resolution");
	}

	public void saveAssignedMergedLabels()
	{
		System.out.println( "Saving assigned merged labels into " + h5Path + ":" + mergedLabelsDataset  );
		H5Utils.saveAssignedSingleElementLabelMultisetLongPair(
				labelMultisetSource,
				labelSource,
				labelSource,
				assignment,
				new File( h5Path ),
				mergedLabelsDataset,
				labelsCellDimensions );
	}

	private class SaveFragmentSegmentAssignment extends SelfRegisteringAction
	{
		public SaveFragmentSegmentAssignment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				saveFragmentSegmentAssignment();
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
		}
	}

	private class SaveFragmentSegmentAssignmentAndPaintedLabels extends SelfRegisteringAction
	{
		public SaveFragmentSegmentAssignmentAndPaintedLabels( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				saveNextId();
				saveFragmentSegmentAssignment();
				savePaintedLabels();
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
			viewer.showMessage( "Saved fragment-segment assignments and painted labels." );
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
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				savePaintedLabels();
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
		}
	}

	private class SaveMergedLabels extends SelfRegisteringAction
	{
		public SaveMergedLabels( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				saveMergedLabels();
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
		}
	}

	private class SaveAssignedMergedLabels extends SelfRegisteringAction
	{
		public SaveAssignedMergedLabels( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				saveAssignedMergedLabels();
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
			viewer.showMessage( "Saved flattened label export." );
		}
	}
}
