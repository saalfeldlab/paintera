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
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

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

import bdv.bigcat.label.FragmentSegmentAssignment;
import bdv.bigcat.label.IdPicker;
import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.bigcat.ui.SelectionOverlay;
import bdv.util.IdService;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class SelectionController
{
	final protected ViewerPanel viewer;
	final protected IdPicker idPicker;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected IdService idService;

	final protected SelectionOverlay selectionOverlay;

	protected long activeFragmentId = 0;
	protected long hoverFragmentId = 0;
	protected RealPoint lastClick = new RealPoint(3);

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public SelectionController(
			final ViewerPanel viewer,
			final IdPicker idPicker,
			final AbstractSaturatedARGBStream colorStream,
			final IdService idService,
			final FragmentSegmentAssignment assignment,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties )
	{
		this.viewer = viewer;
		this.idPicker = idPicker;
		this.colorStream = colorStream;
		this.idService = idService;

		selectionOverlay = new SelectionOverlay( viewer, this, assignment, colorStream );

		inputAdder = config.inputTriggerAdder( inputTriggerMap, "select" );
		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "select" );

		new SelectFragment( "select fragment", "button1" ).register();

		/* no fancy behavior for simple hovering yet */
		viewer.getDisplay().addMouseMotionListener( new HoverFragment() );

		new NewActiveFragmentId( "new fragment", "N" ).register();
		new IncColorSeed( "increase color seed", "C" ).register();
		new DecColorSeed( "decrease color seed", "shift C" ).register();

		inputActionBindings.addActionMap( "select", ksActionMap );
		inputActionBindings.addInputMap( "select", ksInputMap );
	}

	public long getActiveFragmentId()
	{
		return activeFragmentId;
	}

	public long getHoverFragmentId()
	{
		return hoverFragmentId;
	}

	public void setActiveFragmentId( final long id )
	{
		activeFragmentId = id;
		colorStream.setActive( id );
		System.out.println( "activeID = " + activeFragmentId );
	}

	public void setHoverFragmentId( final long id )
	{
		hoverFragmentId = id;
	}

	public SelectionOverlay getSelectionOverlay()
	{
		return selectionOverlay;
	}

	////////////////
	// behavioUrs //
	////////////////

	public BehaviourMap getBehaviourMap()
	{
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap()
	{
		return inputTriggerMap;
	}

	private abstract class SelfRegisteringBehaviour implements Behaviour
	{
		private final String name;

		private final String[] defaultTriggers;

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
			put( ksActionMap );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
		}
	}

	private class SelectFragment extends SelfRegisteringBehaviour implements ClickBehaviour
	{
		public SelectFragment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void click( final int x, final int y )
		{
			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			viewer.displayToGlobalCoordinates(x, y, lastClick);
			setActiveFragmentId( id );
			viewer.requestRepaint();
		}
	}

	private class HoverFragment implements MouseMotionListener
	{
		@Override
		public void mouseDragged( final MouseEvent e ) {}

		@Override
		public void mouseMoved( final MouseEvent e )
		{
			final int x = e.getX();
			final int y = e.getY();

			final long id = idPicker.getIdAtDisplayCoordinate( x, y );
			setHoverFragmentId( id );
			viewer.getDisplay().repaint();
		}
	}

	private class NewActiveFragmentId extends SelfRegisteringAction
	{
		public NewActiveFragmentId( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			synchronized ( viewer )
			{
				setActiveFragmentId( idService.next() );
			}
			viewer.requestRepaint();
		}
	}

	private class IncColorSeed extends SelfRegisteringAction
	{
		public IncColorSeed( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			colorStream.incSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
	}

	private class DecColorSeed extends SelfRegisteringAction
	{
		public DecColorSeed( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			colorStream.decSeed();
			colorStream.clearCache();
			viewer.requestRepaint();
		}
	}
}
