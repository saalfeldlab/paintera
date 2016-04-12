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

import java.awt.Event;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.bigcat.ui.AnnotationOverlay;
import bdv.util.AbstractNamedAction;
import bdv.util.IdService;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.algorithm.kdtree.HyperPlane;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

/**
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class AnnotationController {
	final protected ViewerPanel viewer;
	final private AnnotationOverlay overlay;
	final private Annotations annotations;

	private Annotation selectedAnnotation;
	
	// max distance for nearest annotation search
	private final static int MaxDistance = 20;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder(ksActionMap);
	private final KeyStrokeAdder ksKeyStrokeAdder;

	public BehaviourMap getBehaviourMap() {
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap() {
		return inputTriggerMap;
	}

	public AnnotationController(final ViewerPanel viewer, final Annotations annotations,
			final InputTriggerConfig config, final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties) {
		this.viewer = viewer;
		this.annotations = annotations;
		overlay = new AnnotationOverlay(viewer, annotations, this);
		overlay.setVisible(true);
		inputAdder = config.inputTriggerAdder(inputTriggerMap, "bigcat");

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder(ksInputMap, "bdv");

		// register actions and behaviours here
		new SelectAnnotation("select annotation", "SPACE button1").register();
		new MoveAnnotation("move annotation", "SPACE button1").register();
		new RemoveAnnotation("remove annotation", "SPACE button3").register();
		new AddSynapseAnnotation("add synapse annotation", "SPACE shift button3").register();
		new AddSynapticSiteAnnotation("add synaptic site annotation", "SPACE shift button1").register();

		inputActionBindings.addActionMap("bdv", ksActionMap);
		inputActionBindings.addInputMap("bdv", ksInputMap);
	}

	public AnnotationOverlay getAnnotationOverlay() {

		return overlay;
	}

	public Annotation getSelectedAnnotation() {

		return selectedAnnotation;
	}

	/**
	 * Get the closest annotation to a given display point.
	 * 
	 * @param x
	 * @param y
	 * @param maxDistance Max distance to consider for the search. If no annotation exists in the search area, null is returned.
	 * @return
	 */
	private Annotation getClosestAnnotation(int x, int y, double maxDistance) {

		RealPoint pos = new RealPoint(3);
		viewer.displayToGlobalCoordinates(x, y, pos);
		System.out.println("clicked at global coordinates " + pos);
		
		List< Annotation > closest = annotations.getKNearest(pos, 1);
		
		if (closest.size() == 0)
			return null;
			
		double[] a = new double[3];
		double[] b = new double[3];
		pos.localize(a);
		closest.get(0).getPosition().localize(b);
		double distance = LinAlgHelpers.distance(a, b);
		
		if (distance <= maxDistance)
			return closest.get(0);
			
		return null;
	}

	////////////////
	// behavioUrs //
	////////////////

	private abstract class SelfRegisteringBehaviour implements Behaviour {
		private final String name;

		private final String[] defaultTriggers;

		public SelfRegisteringBehaviour(final String name, final String... defaultTriggers) {
			this.name = name;
			this.defaultTriggers = defaultTriggers;
		}

		public void register() {
			behaviourMap.put(name, this);
			inputAdder.put(name, defaultTriggers);
		}
	}

	private abstract class SelfRegisteringAction extends AbstractNamedAction {
		private final String[] defaultTriggers;

		public SelfRegisteringAction(final String name, final String... defaultTriggers) {
			super(name);
			this.defaultTriggers = defaultTriggers;
		}

		public void register() {
			ksActionAdder.put(this);
			ksKeyStrokeAdder.put(name(), defaultTriggers);
		}
	}

	private class SelectAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {

		public SelectAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {

			System.out.println("Selecting annotation closest to " + x + ", " + y);
			selectedAnnotation = getClosestAnnotation(x, y, MaxDistance);

			viewer.requestRepaint();
		}
	}

	private class RemoveAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {

		public RemoveAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {

			System.out.println("Removing annotation closest to " + x + ", " + y);

			Annotation poorGuy = getClosestAnnotation(x, y, MaxDistance);
			
			if (poorGuy == null)
				return;

			annotations.remove(poorGuy);
			viewer.requestRepaint();
		}
	}

	private class AddSynapseAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {

		public AddSynapseAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {

			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			
			System.out.println("Adding synapse at " + pos);
			
			Synapse synapse = new Synapse(IdService.allocate(), pos, "");
			annotations.add(synapse);

			selectedAnnotation = synapse;

			viewer.requestRepaint();
		}
	}

	private class AddSynapticSiteAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {
		public AddSynapticSiteAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {
		
			if (selectedAnnotation == null || !(selectedAnnotation instanceof Synapse)) {

				System.out.println("select a synapse before adding synaptic sites to it");
				return;
			}
			
			Synapse synapse = (Synapse)selectedAnnotation;
			
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			
			System.out.println("Adding synaptic site at " + pos);
			
			SynapticSite site = new SynapticSite(IdService.allocate(), pos, "");
			site.setSynapse(synapse);
			synapse.addPostSynapticPartner(site);
			annotations.add(site);

			viewer.requestRepaint();
		}
	}
	
	private class MoveAnnotation extends SelfRegisteringBehaviour implements DragBehaviour {
		public MoveAnnotation(final String name, final String ... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void init(int x, int y) {
			
			selectedAnnotation = getClosestAnnotation(x, y, MaxDistance);
			viewer.requestRepaint();
		}

		@Override
		public void drag(int x, int y) {
		
			if (selectedAnnotation == null)
				return;
			
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			selectedAnnotation.setPosition(pos);
			viewer.requestRepaint();
		}

		@Override
		public void end(int x, int y) {

			annotations.markDirty();
		}	
	}

	// define actions and behaviours here
}
