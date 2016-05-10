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
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import bdv.BigDataViewer;
import bdv.bigcat.annotation.Annotation;
import bdv.bigcat.annotation.Annotations;
import bdv.bigcat.annotation.AnnotationsStore;
import bdv.bigcat.annotation.PostSynapticSite;
import bdv.bigcat.annotation.PreSynapticSite;
import bdv.bigcat.annotation.Synapse;
import bdv.bigcat.ui.AnnotationsOverlay;
import bdv.bigcat.ui.AnnotationsWindow;
import bdv.bigcat.util.Selection;
import bdv.util.AbstractNamedAction;
import bdv.util.AbstractNamedAction.NamedActionAdder;
import bdv.util.Affine3DHelpers;
import bdv.util.IdService;
import bdv.viewer.InputActionBindings;
import bdv.viewer.ViewerPanel;

/**
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class AnnotationController implements WindowListener, Selection.SelectionListener<Annotation> {
	
	final protected AnnotationsStore store;
	final protected ViewerPanel viewer;
	final private AnnotationsOverlay overlay;
	final private Annotations annotations;

	private Selection<Annotation> selection = new Selection<Annotation>();
	
	// max distance for nearest annotation search
	private final static int MaxDistance = 1000;

	// for behavioUrs
	private final BehaviourMap behaviourMap = new BehaviourMap();
	private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
	private final InputTriggerAdder inputAdder;

	// for keystroke actions
	private final ActionMap ksActionMap = new ActionMap();
	private final InputMap ksInputMap = new InputMap();
	private final NamedActionAdder ksActionAdder = new NamedActionAdder(ksActionMap);
	private final KeyStrokeAdder ksKeyStrokeAdder;

	private AnnotationsWindow annotationWindow;

	public BehaviourMap getBehaviourMap() {
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap() {
		return inputTriggerMap;
	}

	public AnnotationController(final AnnotationsStore annotationsStore, final BigDataViewer viewer,
			final InputTriggerConfig config, final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties) throws Exception {
		this.viewer = viewer.getViewer();
		this.store = annotationsStore;
		this.annotations = annotationsStore.read();
		this.selection.addSelectionListener(this);
		this.annotationWindow = new AnnotationsWindow(this, this.annotations, this.selection);
		overlay = new AnnotationsOverlay(viewer.getViewer(), annotations, this);
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
		new ShowAnnotationsList("show annotations list", "A").register();
		new GotoAnnotation("goto annotation", "G").register();

		inputActionBindings.addActionMap("bdv", ksActionMap);
		inputActionBindings.addInputMap("bdv", ksInputMap);

		viewer.getViewerFrame().addWindowListener(this);

		// setup annotation window's keybindings to be the same as bdv's
		final InputActionBindings viewerKeybindings = viewer.getViewerFrame()
				.getKeybindings();
		SwingUtilities.replaceUIActionMap(annotationWindow.getRootPane(),
				viewerKeybindings.getConcatenatedActionMap());
		SwingUtilities.replaceUIInputMap(annotationWindow.getRootPane(),
				JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
				viewerKeybindings.getConcatenatedInputMap());
	}

	public AnnotationsOverlay getAnnotationOverlay() {

		return overlay;
	}

	public Annotation getSelectedAnnotation() {

		return selection.getLastAdded();
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

		private static final long serialVersionUID = 1L;

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
			
			Annotation closest = getClosestAnnotation(x, y, MaxDistance);
			if (closest == null)
				return;
			
			selection.clear();
			selection.add(closest);
		}
	}

	private class RemoveAnnotation extends SelfRegisteringAction {

		private static final long serialVersionUID = 1L;

		public RemoveAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			
			System.out.println("deleting annotation(s)");

			for (Annotation a : selection)
				annotations.remove(a);
			selection.clear();
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

			selection.clear();
			selection.add(synapse);
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

			selection.clear();
			selection.add(site);
		}
	}

	private class AddPostSynapticSiteAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {
		public AddPostSynapticSiteAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(int x, int y) {
		
			Annotation active = selection.getLastAdded();
			
			if (active == null || !(active instanceof PreSynapticSite)) {

				viewer.showMessage("select a pre-synaptic site before adding a post-synaptic site to it");
				return;
			}
			
			PreSynapticSite pre = (PreSynapticSite)active;
			
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			
			System.out.println("Adding postsynaptic site at " + pos);
			
			PostSynapticSite site = new PostSynapticSite(IdService.allocate(), pos, "");
			site.setPartner(pre);
			pre.setPartner(site);
			annotations.add(site);
			
			selection.clear();
			selection.add(site);
		}
	}
	
	private class MoveAnnotation extends SelfRegisteringBehaviour implements DragBehaviour {
		public MoveAnnotation(final String name, final String ... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void init(int x, int y) {
			
			Annotation annotation = getClosestAnnotation(x, y, MaxDistance);
			if (annotation == null)
				return;
			
			selection.clear();
			selection.add(annotation);
		}

		@Override
		public void drag(int x, int y) {
		
			Annotation active = selection.getLastAdded();
			if (active == null)
				return;
			
			RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			active.setPosition(pos);
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
			Annotation active = selection.getLastAdded();
			if (active == null)
				return;

			String comment = JOptionPane.showInputDialog(viewer, "Change comment:", active.getComment());
			if (comment == null)
				return;
			active.setComment(comment);
			viewer.requestRepaint();
		}
	}

	private class SaveAnnotations extends SelfRegisteringAction {
		private static final long serialVersionUID = 1L;

		public SaveAnnotations( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed(final ActionEvent e) {

			store.write(annotations);
			viewer.showMessage("Annotations saved");
		}
	}

	private class ShowAnnotationsList extends SelfRegisteringAction {
		private static final long serialVersionUID = 1L;

		public ShowAnnotationsList( final String name, final String ... defaultTriggers )
		{
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			System.out.println("showing annotation window");
			annotationWindow.setVisible(true);
		}
	}

	private class GotoAnnotation extends SelfRegisteringAction {
		private static final long serialVersionUID = 1L;

		public GotoAnnotation(final String name, final String ... defaultTriggers) {
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed(final ActionEvent e) {

			Annotation active = selection.getLastAdded();
			if (active == null)
				return;

			goTo(active.getPosition());
		}
	}

	////////////////////
	// WindowListener //
	////////////////////

	@Override
	public void windowClosed(WindowEvent e) {

		System.out.println("received window close event");
		annotationWindow.dispose();
	}

	@Override
	public void windowActivated(WindowEvent arg0) {
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
	}

	@Override
	public void itemSelected(Annotation t) {

		viewer.requestRepaint();
	}

	@Override
	public void itemUnselected(Annotation t) {

		viewer.requestRepaint();
	}

	@Override
	public void selectionCleared() {

		viewer.requestRepaint();
	}

	public void goTo(RealPoint position) {

		RealPoint currentCenter = new RealPoint(3);
		viewer.displayToGlobalCoordinates(viewer.getWidth() / 2,
				viewer.getHeight() / 2, currentCenter);

		System.out.println("current center is at " + currentCenter);

		double dX = currentCenter.getDoublePosition(0)
				- position.getDoublePosition(0);
		double dY = currentCenter.getDoublePosition(1)
				- position.getDoublePosition(1);
		double dZ = currentCenter.getDoublePosition(2)
				- position.getDoublePosition(2);

		System.out.println("translating by " + dX + ", " + dY + ", " + dZ);

		AffineTransform3D translate = new AffineTransform3D();
		translate.translate(new double[] { dX, dY, dZ });

		synchronized (viewer) {

			AffineTransform3D viewerTransform = new AffineTransform3D();
			viewer.getState().getViewerTransform(viewerTransform);
			AffineTransform3D translated = viewerTransform
					.concatenate(translate);
			viewer.setCurrentViewerTransform(translated);
		}

		viewer.requestRepaint();
	}

	public void setFov(double fov) {

		synchronized (viewer) {

			RealPoint currentCenter = new RealPoint(3);
			viewer.displayToGlobalCoordinates(viewer.getWidth() / 2,
					viewer.getHeight() / 2, currentCenter);

			AffineTransform3D viewerTransform = new AffineTransform3D();
			viewer.getState().getViewerTransform(viewerTransform);

			double currentFov = Math.min(viewer.getWidth(), viewer.getHeight())
					/ Affine3DHelpers.extractScale(viewerTransform, 0);
			double scale = currentFov / fov;

			System.out.println("current fov is " + currentFov
					+ " in smallest dimension, want " + fov + ", scale by "
					+ scale);

			viewerTransform.scale(scale);
			viewer.setCurrentViewerTransform(viewerTransform);

			goTo(currentCenter);
		}

	}
}
