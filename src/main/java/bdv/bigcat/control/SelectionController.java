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

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.ui.AbstractSaturatedARGBStream;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.util.IdService;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class SelectionController
{
	final protected ViewerPanel viewer;
	final protected AbstractSaturatedARGBStream colorStream;
	final protected IdService idService;
	protected long activeFragmentId = 0;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder( ksActionMap );
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public SelectionController(
			final ViewerPanel viewer,
			final AbstractSaturatedARGBStream colorStream,
			final IdService idService,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties)
	{
		this.viewer = viewer;
		this.colorStream = colorStream;
		this.idService = idService;

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "select" );

		new NewActiveFragmentId("new fragment", "N").register();
		new IncColorSeed("increase color seed", "C").register();
		new DecColorSeed("decrease color seed", "shift C").register();

		inputActionBindings.addActionMap( "select", ksActionMap );
		inputActionBindings.addInputMap( "select", ksInputMap );
	}

	public long getActiveFragmentId()
	{
		return activeFragmentId;
	}

	public void setActiveFragmentId( final long id )
	{
		activeFragmentId = id;
		colorStream.setActive( id );
		System.out.println( "activeID = " + activeFragmentId );
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
