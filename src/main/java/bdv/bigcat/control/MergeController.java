/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat.control;

import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.IdPicker;
import bdv.labels.labelset.Label;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class MergeController
{
	final protected ViewerPanel viewer;
	final protected IdPicker idPicker;
	final protected SelectionController selectionController;
	final protected FragmentSegmentAssignment assignment;
	protected RealPoint lastClick = new RealPoint(3);

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final KeyStrokeAdder ksKeyStrokeAdder;

	private class Action {

		public double x, y, z;

		Action(final double x, final double y, final double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private class Merge extends Action {

		public double prevX, prevY, prevZ;
		public long id1, id2;

		Merge(final double x1, final double y1, final double z1, final double x2, final double y2, final double z2, final long id1, final long id2) {
			super(x2, y2, z2);
			this.prevX = x1;
			this.prevY = y1;
			this.prevZ = z1;
			this.id1 = id1;
			this.id2 = id2;
		}
	}
	private class Split extends Action {

		public long id;

		Split(final double x, final double y, final double z, final long id) {
			super(x, y, z);
			this.id = id;
		}
	}
	private class Paint extends Action {

		Paint(final double x, final double y, final double z) {
			super(x, y, z);
		}
	}

	private List< Merge > merges = new LinkedList< Merge >();
	private List< Split > splits = new LinkedList< Split >();
	private List< Paint > paints = new LinkedList< Paint >();
	// general actions
	private List< Action > actions = new LinkedList< Action >();

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	final GsonBuilder gsonBuilder = new GsonBuilder();
	{
		gsonBuilder.registerTypeAdapter( FragmentSegmentAssignment.class, new FragmentSegmentAssignment.FragmentSegmentSerializer() );
		//gsonBuilder.setPrettyPrinting();
	}
	final Gson gson = gsonBuilder.create();

	public MergeController(
			final ViewerPanel viewer,
			final IdPicker idPicker,
			final SelectionController selectionController,
			final FragmentSegmentAssignment assignment,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties )
	{
		this.viewer = viewer;
		this.idPicker = idPicker;
		this.selectionController = selectionController;
		this.assignment = assignment;

		inputAdder = config.inputTriggerAdder( inputTriggerMap, "merge" );
		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "merge" );

		// often used, one modifier
		new NeedMerge("need merge", "shift button1").register();
		new NeedSplit("need split", "shift button3").register();

		// rarely used (subject to removal)
		new NeedPaint("need paint", "SPACE shift button1").register();
		new NeedGeneralAction("need general action", "SPACE shift button3").register();

		// key bindings
		new ExportActions("export assignments", "E").register();

		inputActionBindings.addActionMap( "merge", ksActionMap );
		inputActionBindings.addInputMap( "merge", ksInputMap );
	}

	////////////////
	// behavioUrs //
	////////////////

	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;

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

	private class NeedMerge extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedMerge( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long oldActiveFragmentId = selectionController.getActiveFragmentId();
			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			assignment.mergeFragmentSegments( oldActiveFragmentId, id );
			selectionController.setActiveFragmentId( id );
			viewer.requestRepaint();

			final RealPoint pos = new RealPoint( 3 );
			viewer.displayToGlobalCoordinates( x, y, pos );
			final Merge merge = new Merge(
					lastClick.getDoublePosition( 0 ),
					lastClick.getDoublePosition( 1 ),
					lastClick.getDoublePosition( 2),
					pos.getDoublePosition( 0 ),
					pos.getDoublePosition( 1 ),
					pos.getDoublePosition( 2 ),
					oldActiveFragmentId,
					id );
			merges.add( merge );

			viewer.displayToGlobalCoordinates( x, y, lastClick );
			System.out.println( "recoreded 'need merge' of " + oldActiveFragmentId + " with " + id );
		}
	}

	private class NeedSplit extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedSplit( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates( x, y, lastClick );

			if (id != Label.TRANSPARENT)
				assignment.detachFragment( id );

			selectionController.setActiveFragmentId( id );
			viewer.requestRepaint();

			final Split split = new Split(
					lastClick.getDoublePosition( 0 ),
					lastClick.getDoublePosition( 1 ),
					lastClick.getDoublePosition( 2 ),
					id );
			splits.add( split );

			System.out.println( "recorded 'need-split' of " + id );
		}
	}

	private class NeedPaint extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedPaint( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates( x, y, lastClick );
			selectionController.setActiveFragmentId( id );
			viewer.requestRepaint();

			final Paint paint = new Paint(
					lastClick.getDoublePosition( 0 ),
					lastClick.getDoublePosition( 1 ),
					lastClick.getDoublePosition( 2 ) );
			paints.add( paint );

			System.out.println( "recorded 'need-paint' at " + lastClick );
		}
	}

	private class NeedGeneralAction extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public NeedGeneralAction( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates( x, y, lastClick );
			selectionController.setActiveFragmentId( id );
			viewer.requestRepaint();

			final Action action = new Action(
					lastClick.getDoublePosition( 0 ),
					lastClick.getDoublePosition( 1 ),
					lastClick.getDoublePosition( 2 ) );
			actions.add( action );

			System.out.println( "recorded 'need-general-action' at " + lastClick );
		}
	}

	private class ExportActions extends SelfRegisteringAction
	{
		private static final long serialVersionUID = 1L;

		public ExportActions( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			for ( final Merge merge : merges )
				System.out.println(
						"merge, " + merge.prevX/4.0 +
						", " + merge.prevY/4.0 +
						", " + Math.round(merge.prevZ/40.0) +
						", " + merge.x/4.0 +
						", " + merge.y/4.0 +
						", " + Math.round(merge.z/40.0) +
						", " + merge.id1 +
						", " + merge.id2 );
			for ( final Split split : splits )
				System.out.println(
						"split, " + split.x/4.0 +
						", " + split.y/4.0 +
						", " + Math.round(split.z/40.0) +
						", " + split.id );
			for ( final Paint paint : paints )
				System.out.println(
						"paint, " + paint.x/4.0 +
						", " + paint.y/4.0 +
						", " + Math.round(paint.z/40.0));
			for ( final Action action : actions )
				System.out.println(
						"check, " + action.x/4.0 +
						", " + action.y/4.0 +
						", " + Math.round(action.z/40.0));
		}
	}
}
