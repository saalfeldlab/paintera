package bdv.bigcat.control;

import java.awt.event.ActionEvent;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.ui.AbstractARGBStream;
import bdv.labels.labelset.Label;
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
	final protected AbstractARGBStream colorStream;
	final protected Switch hideConfirmed;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public ConfirmSegmentController(
			final ViewerPanel viewer,
			final SelectionController selectionController,
			final FragmentSegmentAssignment assignment,
			final AbstractARGBStream colorStream,
			final Switch hideConfirmed,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings )
	{
		this.viewer = viewer;
		this.selectionController = selectionController;
		this.assignment = assignment;
		this.colorStream = colorStream;
		this.hideConfirmed = hideConfirmed;
		ksKeyStrokeAdder = config.keyStrokeAdder( ksInputMap, "confirm segment" );

		new ConfirmSegment( "confirm segment", "U" ).register();
		new ToggleConfirmedSegmentVisibility( "toggle confirmed segment visibility", "J" ).register();

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
			put( ksActionMap );
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
			long activeFragmentId;
			synchronized ( viewer )
			{
				activeFragmentId = selectionController.getActiveFragmentId();
				assignment.assignFragments( assignment.getSegment( activeFragmentId ), Label.INVALID );
			}
			viewer.showMessage( "confirmed fragment " + activeFragmentId );
			viewer.requestRepaint();
		}
	}

	private class ToggleConfirmedSegmentVisibility extends SelfRegisteringAction
	{
		public ToggleConfirmedSegmentVisibility( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			boolean hideConfirmedState;
			synchronized ( viewer )
			{
				hideConfirmed.toggleSwitch();
				hideConfirmedState = hideConfirmed.getSwitch();
				colorStream.clearCache();
			}
			viewer.showMessage( ( hideConfirmedState ? "hiding" : "showing" ) + " confirmed fragments" );
			viewer.requestRepaint();
		}
	}
}
