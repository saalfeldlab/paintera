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

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JOptionPane;

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
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.util.IdService;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/**
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class AnnotationController {
	
	final protected AnnotationsStore store;
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
	
	// workaround for BDV freezing after showInputDialog inside action or behaviour
	private Boolean changeComment = false;

	public BehaviourMap getBehaviourMap() {
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap() {
		return inputTriggerMap;
	}

	public AnnotationController(final AnnotationsStore annotationsStore, final ViewerPanel viewer,
			final InputTriggerConfig config, final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties) {
		this.viewer = viewer;
		this.store = annotationsStore;
		this.annotations = annotationsStore.read();
		overlay = new AnnotationOverlay(viewer, annotations, this);
		overlay.setVisible(true);
		inputAdder = config.inputTriggerAdder(inputTriggerMap, "bigcat");

		ksKeyStrokeAdder = keyProperties.keyStrokeAdder(ksInputMap, "bdv");


		// register actions and behaviours here
		new SelectAnnotation("select annotation", "SPACE button1").register();
		new MoveAnnotation("move annotation", "SPACE button1").register();
		new RemoveAnnotation("remove annotation", "DELETE").register();
		new AddPreSynapticSiteAnnotation("add presynaptic site annotation", "SPACE shift button1").register();
		new AddPostSynapticSiteAnnotation("add postsynaptic site annotation", "SPACE shift button3").register();
		new AddSynapseAnnotation("add synapse annotation", "SPACE shift button2").register();
		new ChangeComment("change comment", "C").register();
		new SaveAnnotations("save annotations", "S").register();

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

	private class RemoveAnnotation extends SelfRegisteringAction {

		public RemoveAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			
			System.out.println("deleting annotation");

			if (selectedAnnotation == null)
				return;

			annotations.remove(selectedAnnotation);
			selectedAnnotation = null;
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

	private class AddPreSynapticSiteAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {
		public AddPreSynapticSiteAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {
		
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			
			System.out.println("Adding presynaptic site at " + pos);
			
			PreSynapticSite site = new PreSynapticSite(IdService.allocate(), pos, "");
			annotations.add(site);

			selectedAnnotation = site;
			
			viewer.requestRepaint();
		}
	}

	private class AddPostSynapticSiteAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {
		public AddPostSynapticSiteAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {
		
			if (selectedAnnotation == null || !(selectedAnnotation instanceof PreSynapticSite)) {

				System.out.println("select a pre-synaptic site before adding a post-synaptic site to it");
				return;
			}
			
			PreSynapticSite pre = (PreSynapticSite)selectedAnnotation;
			
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			
			System.out.println("Adding postsynaptic site at " + pos);
			
			PostSynapticSite site = new PostSynapticSite(IdService.allocate(), pos, "");
			site.setPartner(pre);
			pre.setPartner(site);
			annotations.add(site);
			
			selectedAnnotation = site;

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

	private class ChangeComment extends SelfRegisteringAction
	{
		private static final long serialVersionUID = 1L;

		public ChangeComment( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if (selectedAnnotation == null)
				return;

			changeComment = true;
			
			// The following is what we want but it freezes the BDV...
//			String comment = JOptionPane.showInputDialog(viewer, "Change comment:", selectedAnnotation.getComment());
//			if (comment == null)
//				return;
//			selectedAnnotation.setComment(comment);
//			viewer.requestRepaint();
		}
	}

	private class SaveAnnotations extends SelfRegisteringAction
	{
		private static final long serialVersionUID = 1L;

		public SaveAnnotations( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			System.out.println("Saving annotations...");
			store.write(annotations);
			System.out.println("done.");
		}
	}
	
	// workaround
	
	public void changeCommentOfCurrentSelection() {

		synchronized(changeComment) {

			if (selectedAnnotation == null || changeComment == false)
				return;

			changeComment = false;
		}

		String comment = JOptionPane.showInputDialog(viewer, "Change comment:", selectedAnnotation.getComment());
		if (comment == null)
			return;
		selectedAnnotation.setComment(comment);
		viewer.requestRepaint();
	}
}
