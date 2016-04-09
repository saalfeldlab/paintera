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
package bdv.bigcat.annotation;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.ui.AnnotationOverlay;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class AnnotationController
{
	final protected ViewerPanel viewer;
	final private AnnotationOverlay overlay;
	final private Annotations annotations;

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

	public AnnotationController(
			final ViewerPanel viewer,
			final Annotations annotations,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties)
	{
		this.viewer = viewer;
		this.annotations = annotations;
		overlay = new AnnotationOverlay( viewer, annotations );
		overlay.setVisible(true);
		inputAdder = config.inputTriggerAdder( inputTriggerMap, "bigcat" );

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder( ksInputMap, "bdv" );
		
		// register actions and behaviours here
		new SelectAnnotation("select annotation", "button1").register();
		
		inputActionBindings.addActionMap( "bdv", ksActionMap );
		inputActionBindings.addInputMap( "bdv", ksInputMap );
	}

	public AnnotationOverlay getAnnotationOverlay() {
		
		return overlay;
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
			ksActionAdder.put( this );
			ksKeyStrokeAdder.put( name(), defaultTriggers );
		}
	}
	
	private class SelectAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour
	{	
		public SelectAnnotation( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}
		
		@Override
		public void click( int x, int y )
		{
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			System.out.println("clicked at global coordinates " + pos);
			
//			viewer.requestRepaint();
		}
	}
	
	// define actions and behaviours here
}
