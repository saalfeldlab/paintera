package bdv.bigcat.control;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.img.h5.H5Utils;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.LongType;

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class LabelPersistenceController
{
	final protected ViewerPanel viewer;
	final protected RandomAccessibleInterval< LabelMultisetType > labelMultisetSource;
	final protected RandomAccessibleInterval< LongType > labelSource;
	final protected FragmentSegmentAssignment assignment;

	final protected String h5Path;
	final protected String paintedLabelsDataset;
	final protected String mergedLabelsDataset;
	final protected int[] labelsCellDimensions;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder( ksActionMap );
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public LabelPersistenceController(
			final ViewerPanel viewer,
			final RandomAccessibleInterval< LabelMultisetType > labelMultisetSource,
			final RandomAccessibleInterval< LongType > labelSource,
			final FragmentSegmentAssignment assignment,
			final String h5Path,
			final String paintedLabelsDataset,
			final String mergedLabelsDataset,
			final int[] labelsH5CellDimensions,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings )
	{
		this.viewer = viewer;
		this.labelMultisetSource = labelMultisetSource;
		this.labelSource = labelSource;
		this.assignment = assignment;
		this.h5Path = h5Path;
		this.paintedLabelsDataset = paintedLabelsDataset;
		this.mergedLabelsDataset = mergedLabelsDataset;
		this.labelsCellDimensions = labelsH5CellDimensions;
		ksKeyStrokeAdder = config.keyStrokeAdder( ksInputMap, "persistence" );

		new SavePaintedLabels( "save painted labels", "ctrl S" ).register();
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
			ksActionAdder.put( this );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
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
			System.out.println( "Saving painted labels into " + h5Path + ":" + paintedLabelsDataset );
			viewer.showMessage( "Saving painted labels ..." );
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				H5Utils.saveUnsignedLong( labelSource, new File( h5Path ), paintedLabelsDataset, labelsCellDimensions );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
			viewer.showMessage( "... saved merged labels." );
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
			System.out.println( "Saving merged labels into " + h5Path + ":" + mergedLabelsDataset  );
			viewer.showMessage( "Saving merged labels ..." );
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				viewer.showMessage( "Saving merged labels ..." );
				H5Utils.saveSingleElementLabelMultisetLongPair(
						labelMultisetSource,
						labelSource,
						labelSource,
						new File( h5Path ),
						mergedLabelsDataset,
						labelsCellDimensions );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
			viewer.showMessage( "... saved merged labels." );
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
			System.out.println( "Saving assigned merged labels into " + h5Path + ":" + mergedLabelsDataset  );
			viewer.showMessage( "Saving assigned merged labels ..." );
			synchronized ( viewer )
			{
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
				H5Utils.saveAssignedSingleElementLabelMultisetLongPair(
						labelMultisetSource,
						labelSource,
						labelSource,
						assignment,
						new File( h5Path ),
						mergedLabelsDataset,
						labelsCellDimensions );
				viewer.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
			}
			viewer.showMessage( "... saved assigned merged labels." );
		}
	}
}
