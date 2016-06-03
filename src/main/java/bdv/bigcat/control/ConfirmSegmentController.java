package bdv.bigcat.control;

import java.awt.event.ActionEvent;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.labels.labelset.Label;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;

/**
 * Assign the current segment to the confirmed segments group.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class ConfirmSegmentController
{
	final protected ViewerPanel viewer;
	final protected SelectionController selectionController;
	final protected FragmentSegmentAssignment assignment;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder( ksActionMap );
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public ConfirmSegmentController(
			final ViewerPanel viewer,
			final SelectionController selectionController,
			final FragmentSegmentAssignment assignment,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings )
	{
		this.viewer = viewer;
		this.selectionController = selectionController;
		this.assignment = assignment;
		ksKeyStrokeAdder = config.keyStrokeAdder( ksInputMap, "confirm segment" );

		new ConfirmSegment( "confirm segment", "U" ).register();

		inputActionBindings.addActionMap( "confirm segment", ksActionMap );
		inputActionBindings.addInputMap( "confirm segment", ksInputMap );
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

	private class ConfirmSegment extends SelfRegisteringAction
	{
		public ConfirmSegment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				assignment.assignFragments( assignment.getSegment( selectionController.getActiveFragmentId() ), Label.INVALID );
			}
			viewer.requestRepaint();
		}
	}
}
