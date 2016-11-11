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

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTriggerAdder;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.InputActionBindings;

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
import bdv.util.Affine3DHelpers;
import bdv.util.IdService;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

/**
 * @author Jan Funke &lt;jfunke@iri.upc.edu&gt;
 */
public class AnnotationsController implements WindowListener, Selection.SelectionListener<Annotation> {

	final protected AnnotationsStore store;
	final protected ViewerPanel viewer;
	final private AnnotationsOverlay overlay;
	final private Annotations annotations;
	final private IdService idService;

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
	private final KeyStrokeAdder ksKeyStrokeAdder;

	private AnnotationsWindow annotationWindow;

	public BehaviourMap getBehaviourMap() {
		return behaviourMap;
	}

	public InputTriggerMap getInputTriggerMap() {
		return inputTriggerMap;
	}

	public AnnotationsController(
			final AnnotationsStore annotationsStore,
			final BigDataViewer viewer,
			final IdService idService,
			final InputTriggerConfig config,
			final InputActionBindings inputActionBindings,
			final KeyStrokeAdder.Factory keyProperties) throws Exception {

		this.viewer = viewer.getViewer();
		this.store = annotationsStore;
		this.idService = idService;
		this.annotations = annotationsStore.read();
		this.selection.addSelectionListener(this);
		this.annotationWindow = new AnnotationsWindow(this, this.annotations, this.selection);
		this.overlay = new AnnotationsOverlay(viewer.getViewer(), annotations, this);
		this.overlay.setVisible(true);

		inputAdder = config.inputTriggerAdder(inputTriggerMap, "bigcat");
		ksKeyStrokeAdder = keyProperties.keyStrokeAdder(ksInputMap, "bigcat");

		// often used, one modifier
		new SelectAnnotation("select annotation", "control button1").register();
		new MoveAnnotation("move annotation", "control button1").register();

		// less often used, two modifiers
		new AddPreSynapticSiteAnnotation("add presynaptic site annotation", "control shift button1").register();
		new AddPostSynapticSiteAnnotation("add postsynaptic site annotation", "control shift button3").register();
		new AddSynapseAnnotation("add synapse annotation", "control shift button2").register();

		// key bindings
		new ChangeComment("change comment", "control C").register();
		new SaveAnnotations("save annotations", "S").register();
		new ShowAnnotationsList("show annotations list", "A").register();
		new GotoAnnotation("goto annotation", "G").register();
		new ToggleOverlayVisibility("togle overlay visibility", "O").register();
		new RemoveAnnotation("remove annotation", "DELETE").register();

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
	private Annotation getClosestAnnotation(final int x, final int y, final double maxDistance) {

		final RealPoint pos = new RealPoint(3);
		viewer.displayToGlobalCoordinates(x, y, pos);
		System.out.println("clicked at global coordinates " + pos);

		final List< Annotation > closest = annotations.getKNearest(pos, 1);

		if (closest.size() == 0)
			return null;

		final double[] a = new double[3];
		final double[] b = new double[3];
		pos.localize(a);
		closest.get(0).getPosition().localize(b);
		final double distance = LinAlgHelpers.distance(a, b);

		if (distance <= maxDistance)
			return closest.get(0);

		return null;
	}

	public void saveAnnotations() {
		store.write(annotations);
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
			put( ksActionMap );
			ksKeyStrokeAdder.put(name(), defaultTriggers);
		}
	}

	private class SelectAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {

		public SelectAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(final int x, final int y) {

			System.out.println("Selecting annotation closest to " + x + ", " + y);

			final Annotation closest = getClosestAnnotation(x, y, MaxDistance);
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

			for (final Annotation a : selection)
				annotations.remove(a);
			selection.clear();
		}
	}

	private class AddSynapseAnnotation extends SelfRegisteringBehaviour implements ClickBehaviour {

		public AddSynapseAnnotation(final String name, final String... defaultTriggers) {
			super(name, defaultTriggers);
		}

		@Override
		public void click(final int x, final int y) {

			final RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);

			System.out.println("Adding synapse at " + pos);

			final Synapse synapse = new Synapse(idService.next(), pos, "");
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
		public void click(final int x, final int y) {

			final RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);

			System.out.println("Adding presynaptic site at " + pos);

			final PreSynapticSite site = new PreSynapticSite(idService.next(), pos, "");
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
		public void click(final int x, final int y) {

			final Annotation active = selection.getLastAdded();

			if (active == null || !(active instanceof PreSynapticSite)) {

				viewer.showMessage("select a pre-synaptic site before adding a post-synaptic site to it");
				return;
			}

			final PreSynapticSite pre = (PreSynapticSite)active;

			final RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);

			System.out.println("Adding postsynaptic site at " + pos);

			final PostSynapticSite site = new PostSynapticSite(idService.next(), pos, "");
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
		public void init(final int x, final int y) {

			final Annotation annotation = getClosestAnnotation(x, y, MaxDistance);
			if (annotation == null)
				return;

			selection.clear();
			selection.add(annotation);
		}

		@Override
		public void drag(final int x, final int y) {

			final Annotation active = selection.getLastAdded();
			if (active == null)
				return;

			final RealPoint pos = new RealPoint(3);
			viewer.displayToGlobalCoordinates(x, y, pos);
			active.setPosition(pos);
			viewer.requestRepaint();
		}

		@Override
		public void end(final int x, final int y) {

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
			final Annotation active = selection.getLastAdded();
			if (active == null)
				return;

			final String comment = JOptionPane.showInputDialog(viewer, "Change comment:", active.getComment());
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
			synchronized (viewer) {
				viewer.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				saveAnnotations();
				viewer.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			}
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

			final Annotation active = selection.getLastAdded();
			if (active == null)
				return;

			goTo(active.getPosition());
		}
	}

	private class ToggleOverlayVisibility extends SelfRegisteringAction {
		private static final long serialVersionUID = 1L;

		public ToggleOverlayVisibility(final String name, final String ... defaultTriggers) {
			super( name, defaultTriggers );
		}

		@Override
		public void actionPerformed(final ActionEvent e) {

			overlay.setVisible(!overlay.isVisible());
		}
	}

	////////////////////
	// WindowListener //
	////////////////////

	@Override
	public void windowClosed(final WindowEvent e) {

		System.out.println("received window close event");
		annotationWindow.dispose();
	}

	@Override
	public void windowActivated(final WindowEvent arg0) {
	}

	@Override
	public void windowClosing(final WindowEvent arg0) {
	}

	@Override
	public void windowDeactivated(final WindowEvent arg0) {
	}

	@Override
	public void windowDeiconified(final WindowEvent arg0) {
	}

	@Override
	public void windowIconified(final WindowEvent arg0) {
	}

	@Override
	public void windowOpened(final WindowEvent arg0) {
	}

	@Override
	public void itemSelected(final Annotation t) {

		viewer.requestRepaint();
	}

	@Override
	public void itemUnselected(final Annotation t) {

		viewer.requestRepaint();
	}

	@Override
	public void selectionCleared() {

		viewer.requestRepaint();
	}

	public void goTo(final RealPoint position) {

		final RealPoint currentCenter = new RealPoint(3);
		viewer.displayToGlobalCoordinates(viewer.getWidth() / 2,
				viewer.getHeight() / 2, currentCenter);

		System.out.println("current center is at " + currentCenter);

		final double dX = currentCenter.getDoublePosition(0)
				- position.getDoublePosition(0);
		final double dY = currentCenter.getDoublePosition(1)
				- position.getDoublePosition(1);
		final double dZ = currentCenter.getDoublePosition(2)
				- position.getDoublePosition(2);

		System.out.println("translating by " + dX + ", " + dY + ", " + dZ);

		final AffineTransform3D translate = new AffineTransform3D();
		translate.translate(new double[] { dX, dY, dZ });

		synchronized (viewer) {

			final AffineTransform3D viewerTransform = new AffineTransform3D();
			viewer.getState().getViewerTransform(viewerTransform);
			final AffineTransform3D translated = viewerTransform
					.concatenate(translate);
			viewer.setCurrentViewerTransform(translated);
		}

		viewer.requestRepaint();
	}

	public void setFov(final double fov) {

		synchronized (viewer) {

			final RealPoint currentCenter = new RealPoint(3);
			viewer.displayToGlobalCoordinates(viewer.getWidth() / 2,
					viewer.getHeight() / 2, currentCenter);

			final AffineTransform3D viewerTransform = new AffineTransform3D();
			viewer.getState().getViewerTransform(viewerTransform);

			final double currentFov = Math.min(viewer.getWidth(), viewer.getHeight())
					/ Affine3DHelpers.extractScale(viewerTransform, 0);
			final double scale = currentFov / fov;

			System.out.println("current fov is " + currentFov
					+ " in smallest dimension, want " + fov + ", scale by "
					+ scale);

			viewerTransform.scale(scale);
			viewer.setCurrentViewerTransform(viewerTransform);

			goTo(currentCenter);
		}

	}
}
