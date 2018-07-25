package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import bdv.fx.viewer.MultiBoxOverlayRendererFX;
import bdv.fx.viewer.ViewerPanelFX;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedColumn;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedRow;
import org.janelia.saalfeldlab.fx.ortho.GridResizer;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.ortho.ViewerAxis;
import org.janelia.saalfeldlab.paintera.control.CurrentSourceRefreshMeshes;
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle;
import org.janelia.saalfeldlab.paintera.control.FitToInterval;
import org.janelia.saalfeldlab.paintera.control.Merges;
import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.OrthoViewCoordinateDisplayListener;
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener;
import org.janelia.saalfeldlab.paintera.control.Paint;
import org.janelia.saalfeldlab.paintera.control.RunWhenFirstElementIsAdded;
import org.janelia.saalfeldlab.paintera.control.Selection;
import org.janelia.saalfeldlab.paintera.control.ShowOnlySelectedInStreamToggle;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.ui.ARGBStreamSeedSetter;
import org.janelia.saalfeldlab.paintera.ui.ToggleMaximize;
import org.janelia.saalfeldlab.paintera.ui.opendialog.PainteraOpenDialogEventHandler;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PainteraDefaultHandlers
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final PainteraBaseView baseView;

	@SuppressWarnings("unused")
	private final KeyTracker keyTracker;

	private final MouseTracker mouseTracker;

	private final OrthogonalViews<Viewer3DFX> orthogonalViews;

	private final SourceInfo sourceInfo;

	private final IntegerBinding numSources;

	@SuppressWarnings("unused")
	private final BooleanBinding hasSources;

	private final Navigation navigation;

	private final Merges merges;

	private final Paint paint;

	private final Selection selection;

	private final Consumer<OnEnterOnExit> onEnterOnExit;

	@SuppressWarnings("unused")
	private final PainteraOpenDialogEventHandler openDialogHandler;

	private final ToggleMaximize toggleMaximizeTopLeft;

	private final ToggleMaximize toggleMaximizeTopRight;

	private final ToggleMaximize toggleMaximizeBottomLeft;

	private final ToggleMaximize toggleMaximizeBottomRight;

	private final MultiBoxOverlayRendererFX[] multiBoxes;

	private final GridResizer resizer;

	private final ObservableBooleanValue bottomLeftNeedsZNormal;

	public PainteraDefaultHandlers(
			final PainteraBaseView baseView,
			final KeyTracker keyTracker,
			final MouseTracker mouseTracker,
			final BorderPaneWithStatusBars paneWithStatus,
			final String projectDirectory,
			final GridConstraintsManager gridConstraintsManager)
	{
		this.baseView = baseView;
		this.keyTracker = keyTracker;
		this.mouseTracker = mouseTracker;
		this.orthogonalViews = baseView.orthogonalViews();
		this.sourceInfo = baseView.sourceInfo();
		this.numSources = Bindings.size(sourceInfo.trackSources());
		this.hasSources = numSources.greaterThan(0);

		this.navigation = new Navigation(
				baseView.manager(),
				v -> viewerToTransforms.get(v).displayTransform(),
				v -> viewerToTransforms.get(v).globalToViewerTransform(),
				keyTracker
		);
		this.merges = new Merges(sourceInfo, keyTracker);
		this.paint = new Paint(
				sourceInfo,
				keyTracker,
				baseView.manager(),
				baseView.orthogonalViews()::requestRepaint,
				baseView.getPaintQueue()
		);
		this.selection = new Selection(sourceInfo, keyTracker);

		this.onEnterOnExit = createOnEnterOnExit(paneWithStatus.currentFocusHolder());
		onEnterOnExit.accept(navigation.onEnterOnExit());
		onEnterOnExit.accept(selection.onEnterOnExit());
		onEnterOnExit.accept(merges.onEnterOnExit());
		onEnterOnExit.accept(paint.onEnterOnExit());

		grabFocusOnMouseOver(
				baseView.orthogonalViews().topLeft().viewer(),
				baseView.orthogonalViews().topRight().viewer(),
				baseView.orthogonalViews().bottomLeft().viewer()
		                    );

		this.openDialogHandler = addPainteraOpenDialogHandler(
				baseView,
				keyTracker,
				projectDirectory,
				KeyCode.CONTROL,
				KeyCode.O
		                                                     );

		this.toggleMaximizeTopLeft = toggleMaximizeNode(gridConstraintsManager, 0, 0);
		this.toggleMaximizeTopRight = toggleMaximizeNode(gridConstraintsManager, 1, 0);
		this.toggleMaximizeBottomLeft = toggleMaximizeNode(gridConstraintsManager, 0, 1);
		this.toggleMaximizeBottomRight = toggleMaximizeNode(gridConstraintsManager, 1, 1);

		viewerToTransforms.put(orthogonalViews.topLeft().viewer(), orthogonalViews.topLeft());
		viewerToTransforms.put(orthogonalViews.topRight().viewer(), orthogonalViews.topRight());
		viewerToTransforms.put(orthogonalViews.bottomLeft().viewer(), orthogonalViews.bottomLeft());

		multiBoxes = new MultiBoxOverlayRendererFX[] {
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().topLeft().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources()
				),
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().topRight().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources()
				),
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().bottomLeft().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources()
				)
		};

		multiBoxes[0].isVisibleProperty().bind(baseView.orthogonalViews().topLeft().viewer().focusedProperty());
		multiBoxes[1].isVisibleProperty().bind(baseView.orthogonalViews().topRight().viewer().focusedProperty());
		multiBoxes[2].isVisibleProperty().bind(baseView.orthogonalViews().bottomLeft().viewer().focusedProperty());

		orthogonalViews.topLeft().viewer().getDisplay().addOverlayRenderer(multiBoxes[0]);
		orthogonalViews.topRight().viewer().getDisplay().addOverlayRenderer(multiBoxes[1]);
		orthogonalViews.bottomLeft().viewer().getDisplay().addOverlayRenderer(multiBoxes[2]);

		updateDisplayTransformOnResize(baseView.orthogonalViews(), baseView.manager());

		final Pane              borderPane    = paneWithStatus.getPane();
		final EventFX<KeyEvent> toggleSideBar = EventFX.KEY_RELEASED(
				"toggle sidebar",
				e -> paneWithStatus.toggleSideBar(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.P)
		                                                            );
		borderPane.sceneProperty().addListener((obs, oldv, newv) -> newv.addEventHandler(
				KeyEvent.KEY_PRESSED,
				toggleSideBar::handle
		                                                                                ));

		EventFX.KEY_PRESSED(
				"toggle interpolation",
				e -> toggleInterpolation(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.I)
		                   ).installInto(borderPane);
		EventFX.KEY_PRESSED(
				"cycle current source",
				e -> sourceInfo.incrementCurrentSourceIndex(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.TAB)
		                   ).installInto(borderPane);
		EventFX.KEY_PRESSED(
				"backwards cycle current source",
				e -> sourceInfo.decrementCurrentSourceIndex(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB)
		                   ).installInto(borderPane);

		this.resizer = new GridResizer(gridConstraintsManager, 5, baseView.pane(), keyTracker);
		this.resizer.installInto(baseView.pane());

		final ObjectProperty<Source<?>> currentSource = sourceInfo.currentSourceProperty();

		final OrthogonalViewsValueDisplayListener vdl = new OrthogonalViewsValueDisplayListener(
				paneWithStatus::setCurrentValue,
				currentSource,
				s -> sourceInfo.getState(s).interpolationProperty().get()
		);

		final OrthoViewCoordinateDisplayListener cdl = new OrthoViewCoordinateDisplayListener(
				paneWithStatus::setViewerCoordinateStatus,
				paneWithStatus::setWorldCoorinateStatus
		);

		onEnterOnExit.accept(new OnEnterOnExit(vdl.onEnter(), vdl.onExit()));
		onEnterOnExit.accept(new OnEnterOnExit(cdl.onEnter(), cdl.onExit()));

		onEnterOnExit.accept(new ARGBStreamSeedSetter(sourceInfo, keyTracker).onEnterOnExit());

		sourceInfo.trackSources().addListener(FitToInterval.fitToIntervalWhenSourceAddedListener(
				baseView.manager(),
				baseView.orthogonalViews().topLeft().viewer().widthProperty()::get
		                                                                                        ));
		sourceInfo.trackSources().addListener(new RunWhenFirstElementIsAdded<>(c -> baseView.viewer3D()
				.setInitialTransformToInterval(
				sourceIntervalInWorldSpace(c.getAddedSubList().get(0)))));

		EventFX.KEY_PRESSED(
				"maximize",
				e -> toggleMaximizeTopLeft.toggleFullScreen(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.M)
		                   ).installInto(orthogonalViews.topLeft().viewer());
		EventFX.KEY_PRESSED(
				"maximize",
				e -> toggleMaximizeTopRight.toggleFullScreen(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.M)
		                   ).installInto(orthogonalViews.topRight().viewer());
		EventFX.KEY_PRESSED(
				"maximize",
				e -> toggleMaximizeBottomLeft.toggleFullScreen(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.M)
		                   ).installInto(orthogonalViews.bottomLeft().viewer());
		EventFX.KEY_PRESSED(
				"maximize",
				e -> toggleMaximizeBottomRight.toggleFullScreen(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.M)
		                   ).installInto(baseView.viewer3D());

		final CurrentSourceVisibilityToggle csv = new CurrentSourceVisibilityToggle(sourceInfo.currentState());
		EventFX.KEY_PRESSED(
				"toggle visibility",
				e -> csv.toggleIsVisible(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.V)
		                   ).installInto(borderPane);

		final ShowOnlySelectedInStreamToggle sosist = new ShowOnlySelectedInStreamToggle(
				sourceInfo.currentState()::get,
				sourceInfo.removedSourcesTracker()
		);
		EventFX.KEY_PRESSED(
				"toggle non-selected labels visibility",
				e -> sosist.toggleNonSelectionVisibility(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT, KeyCode.V)
		                   ).installInto(borderPane);

		EventFX.KEY_PRESSED(
				"toggle maximize bottom row",
				e -> {
					gridConstraintsManager.maximize(MaximizedRow.BOTTOM, 0);
				},
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT)
		                   ).installInto(paneWithStatus.getPane());

		bottomLeftNeedsZNormal = Bindings.createBooleanBinding(
				() -> MaximizedColumn.NONE.equals(gridConstraintsManager.getMaximizedColumn()) && MaximizedRow.BOTTOM
						.equals(
						gridConstraintsManager.getMaximizedRow()),
				gridConstraintsManager.observeMaximizedColumn(),
				gridConstraintsManager.observeMaximizedRow()
		                                                      );

		final AffineTransformWithListeners bottomLeftGlobalToViewer = orthogonalViews.bottomLeft()
				.globalToViewerTransform();
		bottomLeftNeedsZNormal.addListener((obs, oldv, newv) -> bottomLeftGlobalToViewer.setTransform(ViewerAxis
				.globalToViewer(
				newv ? ViewerAxis.Z : ViewerAxis.Y)));

		if (gridConstraintsManager.isFullScreen() && GridConstraintsManager.MaximizedRow.BOTTOM.equals(
				gridConstraintsManager.getMaximizedRow()) && GridConstraintsManager.MaximizedColumn.NONE.equals(
				gridConstraintsManager.getMaximizedColumn()))
		{
			orthogonalViews.bottomLeft().globalToViewerTransform().setTransform(ViewerAxis.globalToViewer(ViewerAxis
					.Z));
		}

		final CurrentSourceRefreshMeshes meshRefresher = new CurrentSourceRefreshMeshes(sourceInfo.currentState()
				::get);
		EventFX.KEY_PRESSED(
				"refresh meshes",
				e -> meshRefresher.refresh(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.R)
		                   ).installInto(paneWithStatus.getPane());

		// TODO does MouseEvent.getPickResult make the coordinate tracker
		// obsolete?
		final MeshesGroupContextMenu contextMenuFactory = new MeshesGroupContextMenu(
				baseView.manager(),
				baseView.viewer3D().coordinateTracker()
		);
		baseView.viewer3D().addEventHandler(
				MouseEvent.MOUSE_CLICKED,
				e -> {
					LOG.debug("Handling event {}", e);
					if (MouseButton.SECONDARY.equals(e.getButton()) && e.getClickCount() == 1 && !mouseTracker
							.isDragging())
					{
						LOG.debug("Check passed for event {}", e);
						e.consume();
						final ContextMenu menu = contextMenuFactory.createMenu();
						menu.show(baseView.viewer3D(), e.getScreenX(), e.getScreenY());
					}
				}
		                                   );

	}

	private final Map<ViewerPanelFX, ViewerAndTransforms> viewerToTransforms = new HashMap<>();

	public static DisplayTransformUpdateOnResize[] updateDisplayTransformOnResize(final OrthogonalViews<?> views,
	                                                                              final Object lock)
	{
		return new DisplayTransformUpdateOnResize[] {
				updateDisplayTransformOnResize(views.topLeft(), lock),
				updateDisplayTransformOnResize(views.topRight(), lock),
				updateDisplayTransformOnResize(views.bottomLeft(), lock)
		};
	}

	public static DisplayTransformUpdateOnResize updateDisplayTransformOnResize(final ViewerAndTransforms vat, final
	Object lock)
	{
		final ViewerPanelFX                  viewer           = vat.viewer();
		final AffineTransformWithListeners   displayTransform = vat.displayTransform();
		final DisplayTransformUpdateOnResize updater          = new DisplayTransformUpdateOnResize(
				displayTransform,
				viewer.widthProperty(),
				viewer.heightProperty(),
				lock
		);
		updater.listen();
		return updater;
	}

	public static ObservableObjectValue<ViewerAndTransforms> currentFocusHolder(final OrthogonalViews<?> views)
	{
		final ViewerAndTransforms     tl      = views.topLeft();
		final ViewerAndTransforms     tr      = views.topRight();
		final ViewerAndTransforms     bl      = views.bottomLeft();
		final ReadOnlyBooleanProperty focusTL = tl.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusTR = tr.viewer().focusedProperty();
		final ReadOnlyBooleanProperty focusBL = bl.viewer().focusedProperty();

		return Bindings.createObjectBinding(
				() -> {
					return focusTL.get() ? tl : focusTR.get() ? tr : focusBL.get() ? bl : null;
				},
				focusTL,
				focusTR,
				focusBL
		                                   );

	}

	public static Consumer<OnEnterOnExit> createOnEnterOnExit(final ObservableObjectValue<ViewerAndTransforms>
			                                                          currentFocusHolder)
	{
		final List<OnEnterOnExit> onEnterOnExits = new ArrayList<>();

		final ChangeListener<ViewerAndTransforms> onEnterOnExit = (obs, oldv, newv) -> {
			if (oldv != null)
			{
				onEnterOnExits.stream().map(OnEnterOnExit::onExit).forEach(e -> e.accept(oldv.viewer()));
			}
			if (newv != null)
			{
				onEnterOnExits.stream().map(OnEnterOnExit::onEnter).forEach(e -> e.accept(newv.viewer()));
			}
		};

		currentFocusHolder.addListener(onEnterOnExit);

		return onEnterOnExits::add;
	}

	public static void grabFocusOnMouseOver(final Node... nodes)
	{
		grabFocusOnMouseOver(Arrays.asList(nodes));
	}

	public static void grabFocusOnMouseOver(final Collection<Node> nodes)
	{
		nodes.forEach(PainteraDefaultHandlers::grabFocusOnMouseOver);
	}

	public static void grabFocusOnMouseOver(final Node node)
	{
		node.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> node.requestFocus());
	}

	public void toggleInterpolation()
	{
		final Source<?> source = sourceInfo.currentSourceProperty().get();
		if (source == null) { return; }
		final ObjectProperty<Interpolation> ip = sourceInfo.getState(source).interpolationProperty();
		ip.set(ip.get().equals(Interpolation.NLINEAR) ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR);
		baseView.orthogonalViews().requestRepaint();
	}

	public static Interval sourceIntervalInWorldSpace(final Source<?> source)
	{
		final double[]          min = Arrays.stream(Intervals.minAsLongArray(source.getSource(
				0,
				0
		                                                                                     ))).asDoubleStream()
				.toArray();
		final double[]          max = Arrays.stream(Intervals.maxAsLongArray(source.getSource(
				0,
				0
		                                                                                     ))).asDoubleStream()
				.toArray();
		final AffineTransform3D tf  = new AffineTransform3D();
		source.getSourceTransform(0, 0, tf);
		tf.apply(min, min);
		tf.apply(max, max);
		return Intervals.smallestContainingInterval(new FinalRealInterval(min, max));
	}

	public static void setFocusTraversable(
			final OrthogonalViews<?> view,
			final boolean isTraversable)
	{
		view.topLeft().viewer().setFocusTraversable(isTraversable);
		view.topRight().viewer().setFocusTraversable(isTraversable);
		view.bottomLeft().viewer().setFocusTraversable(isTraversable);
		view.grid().getBottomRight().setFocusTraversable(isTraversable);
	}

	public static PainteraOpenDialogEventHandler addPainteraOpenDialogHandler(
			final PainteraBaseView baseView,
			final KeyTracker keyTracker,
			final String projectDirectory,
			final KeyCode... triggers)
	{

		assert triggers.length > 0;

		final PainteraOpenDialogEventHandler handler = new PainteraOpenDialogEventHandler(
				baseView,
				baseView.orthogonalViews().sharedQueue(),
				e -> keyTracker.areOnlyTheseKeysDown(triggers),
				projectDirectory
		);
		baseView.pane().addEventHandler(KeyEvent.KEY_PRESSED, handler);
		return handler;

	}

	public static ToggleMaximize toggleMaximizeNode(
			final GridConstraintsManager manager,
			final int column,
			final int row)
	{
		return new ToggleMaximize(
				manager,
				MaximizedColumn.fromIndex(column),
				MaximizedRow.fromIndex(row)
		);
	}

	public Navigation navigation()
	{
		return this.navigation;
	}

}
