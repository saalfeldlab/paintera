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

package bdv.bigcat.ui;

import java.awt.event.ActionEvent;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.viewer.ViewerPanel;

public class EditActions {

	public static final String SELECT_FRAGMENT = "select fragment";
	public static final String CLEAR_SELECTION = "clear selection";
	
	public static final String MERGE = "merge selected fragments";
	
	/**
	 * Create edit actions and install them in the specified
	 * {@link InputActionBindings}.
	 *
	 * @param inputActionBindings
	 *            {@link InputMap} and {@link ActionMap} are installed here.
	 * @param viewer
	 *            Edit actions are targeted at this {@link ViewerPanel}.
	 * @param keyProperties
	 *            user-defined key-bindings.
	 */
	public static void installActionBindings(
			final InputActionBindings inputActionBindings,
			final ViewerPanel viewer,
			final KeyStrokeAdder.Factory keyProperties)
	{
		inputActionBindings.addActionMap("edit", createActionMap(viewer));
		inputActionBindings.addInputMap("edit", createInputMap(keyProperties));
	}
	
	public static InputMap createInputMap(final KeyStrokeAdder.Factory keyProperties)
	{
		final InputMap inputMap = new InputMap();
		final KeyStrokeAdder map = keyProperties.keyStrokeAdder(inputMap, "bdv");

		// How can we add mouse events?
		//map.put(SELECT_FRAGMENT, ???)
		map.put(CLEAR_SELECTION, "c");
		map.put(MERGE, "m");
		
		return inputMap;
	}
	
	public static ActionMap createActionMap( final ViewerPanel viewer )
	{
		ActionMap actionMap = new ActionMap();
		
		new SelectFragmentAction(viewer).put( actionMap );
		new ClearSelectionAction(viewer).put( actionMap );
		new MergeAction(viewer).put( actionMap );
			
		return actionMap;
	}
	
	private static abstract class EditAction extends AbstractNamedAction
	{
		protected final ViewerPanel viewer;

		public EditAction(final String name, final ViewerPanel viewer)
		{
			super(name);
			this.viewer = viewer;
		}

		private static final long serialVersionUID = 1L;
	}
	
	public static class SelectFragmentAction extends EditAction
	{
		public SelectFragmentAction(final ViewerPanel viewer)
		{
			super(SELECT_FRAGMENT, viewer);
		}

		@Override
		public void actionPerformed(final ActionEvent e)
		{
			// hm... what to do here? we could just pass on the event's coordinates and leave everything else to the LabelMergeSplitController, and let it do as before
		}

		private static final long serialVersionUID = 1L;
	}
	
	public static class ClearSelectionAction extends EditAction
	{
		public ClearSelectionAction(final ViewerPanel viewer)
		{
			super(CLEAR_SELECTION, viewer);
		}

		@Override
		public void actionPerformed(final ActionEvent e)
		{
			// pass on to LabelMergeSplitController
		}

		private static final long serialVersionUID = 1L;
	}
	
	public static class MergeAction extends EditAction
	{
		public MergeAction(final ViewerPanel viewer)
		{
			super(MERGE, viewer);
		}

		@Override
		public void actionPerformed(final ActionEvent e)
		{
			// pass on to LabelMergeSplitController
		}

		private static final long serialVersionUID = 1L;
	}
}
