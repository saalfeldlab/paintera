package org.janelia.saalfeldlab.paintera;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.multibox.MultiBoxOverlayRendererFX;
import bdv.fx.viewer.scalebar.ScaleBarOverlayConfig;
import bdv.fx.viewer.scalebar.ScaleBarOverlayRenderer;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Affine;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.janelia.saalfeldlab.fx.event.DelegateEventHandlers;
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
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.paintera.config.BookmarkConfig;
import org.janelia.saalfeldlab.paintera.config.BookmarkSelectionDialog;
import org.janelia.saalfeldlab.paintera.control.CurrentSourceVisibilityToggle;
import org.janelia.saalfeldlab.paintera.control.FitToInterval;
import org.janelia.saalfeldlab.paintera.control.Navigation;
import org.janelia.saalfeldlab.paintera.control.OrthoViewCoordinateDisplayListener;
import org.janelia.saalfeldlab.paintera.control.OrthogonalViewsValueDisplayListener;
import org.janelia.saalfeldlab.paintera.control.RunWhenFirstElementIsAdded;
import org.janelia.saalfeldlab.paintera.control.ShowOnlySelectedInStreamToggle;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.control.actions.NavigationActionType;
import org.janelia.saalfeldlab.paintera.control.navigation.AffineTransformWithListeners;
import org.janelia.saalfeldlab.paintera.control.navigation.DisplayTransformUpdateOnResize;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.ARGBStreamSeedSetter;
import org.janelia.saalfeldlab.paintera.ui.ToggleMaximize;
import org.janelia.saalfeldlab.paintera.ui.dialogs.create.CreateDatasetHandler;
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenu;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class PainteraDefaultHandlers
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final EventHandler<Event> DEFAULT_HANDLER = e -> {
		LOG.debug("Default event handler: Use if no source is present");
	};

	private final PainteraBaseView baseView;

	private final PainteraGateway gateway = new PainteraGateway();

	@SuppressWarnings("unused")
	private final KeyTracker keyTracker;

	private final MouseTracker mouseTracker;

	private final OrthogonalViews<Viewer3DFX> orthogonalViews;

	private final SourceInfo sourceInfo;

	private final IntegerBinding numSources;

	@SuppressWarnings("unused")
	private final BooleanBinding hasSources;

	private final Navigation navigation;

	private final Consumer<OnEnterOnExit> onEnterOnExit;

	private final ToggleMaximize toggleMaximizeTopLeft;
	private final ToggleMaximize toggleMaximizeTopRight;
	private final ToggleMaximize toggleMaximizeBottomLeft;

	private final MultiBoxOverlayRendererFX[] multiBoxes;

	private final GridResizer resizer;

	private final ObjectProperty<Interpolation> globalInterpolationProperty = new SimpleObjectProperty<>();

	private final EventHandler<KeyEvent> openDatasetContextMenuHandler;

	private final ObjectBinding<EventHandler<Event>> sourceSpecificGlobalEventHandler;

	private final ObjectBinding<EventHandler<Event>> sourceSpecificGlobalEventFilter;

	private final ObjectBinding<EventHandler<Event>> sourceSpecificViewerEventHandler;

	private final ObjectBinding<EventHandler<Event>> sourceSpecificViewerEventFilter;

	private final ScaleBarOverlayConfig scaleBarConfig = new ScaleBarOverlayConfig();

	private final List<ScaleBarOverlayRenderer> scaleBarOverlays = Arrays.asList(
		new ScaleBarOverlayRenderer(scaleBarConfig),
		new ScaleBarOverlayRenderer(scaleBarConfig),
		new ScaleBarOverlayRenderer(scaleBarConfig));

	private final BookmarkConfig bookmarkConfig = new BookmarkConfig();

	public EventHandler<Event> getSourceSpecificGlobalEventHandler() {
		return DelegateEventHandlers.fromSupplier(sourceSpecificGlobalEventHandler::get);
	}

	public EventHandler<Event> getSourceSpecificGlobalEventFilter() {
		return DelegateEventHandlers.fromSupplier(sourceSpecificGlobalEventFilter::get);
	}

	public EventHandler<Event> getSourceSpecificViewerEventHandler() {
		return DelegateEventHandlers.fromSupplier(sourceSpecificViewerEventHandler::get);
	}

	public EventHandler<Event> getSourceSpecificViewerEventFilter() {
		return DelegateEventHandlers.fromSupplier(sourceSpecificViewerEventFilter::get);
	}

	public PainteraDefaultHandlers(
			final PainteraBaseView baseView,
			final KeyTracker keyTracker,
			final MouseTracker mouseTracker,
			final BorderPaneWithStatusBars paneWithStatus,
			final Supplier<String> projectDirectory,
			final GridConstraintsManager gridConstraintsManager)
	{
		this.baseView = baseView;
		this.keyTracker = keyTracker;
		this.mouseTracker = mouseTracker;
		this.orthogonalViews = baseView.orthogonalViews();
		this.sourceInfo = baseView.sourceInfo();
		this.numSources = Bindings.size(sourceInfo.trackSources());
		this.hasSources = numSources.greaterThan(0);

		final ObservableObjectValue<SourceState<?, ?>> currentState = sourceInfo.currentState();
		this.sourceSpecificGlobalEventHandler = Bindings.createObjectBinding(
				() -> Optional.ofNullable(currentState.get()).map(s -> s.stateSpecificGlobalEventHandler(baseView, keyTracker)).orElse(DEFAULT_HANDLER),
				currentState);
		this.sourceSpecificGlobalEventFilter = Bindings.createObjectBinding(
				() -> Optional.ofNullable(currentState.get()).map(s -> s.stateSpecificGlobalEventFilter(baseView, keyTracker)).orElse(DEFAULT_HANDLER),
				currentState);
		this.sourceSpecificViewerEventHandler = Bindings.createObjectBinding(
				() -> Optional.ofNullable(currentState.get()).map(s -> s.stateSpecificViewerEventHandler(baseView, keyTracker)).orElse(DEFAULT_HANDLER),
				currentState);
		this.sourceSpecificViewerEventFilter = Bindings.createObjectBinding(
				() -> Optional.ofNullable(currentState.get()).map(s -> s.stateSpecificViewerEventFilter(baseView, keyTracker)).orElse(DEFAULT_HANDLER),
				currentState);

		this.navigation = new Navigation(
				baseView.manager(),
				v -> viewerToTransforms.get(v).displayTransform(),
				v -> viewerToTransforms.get(v).globalToViewerTransform(),
				keyTracker,
				baseView.allowedActionsProperty()
		);

		this.onEnterOnExit = createOnEnterOnExit(paneWithStatus.currentFocusHolder());
		onEnterOnExit.accept(navigation.onEnterOnExit());
		baseView.orthogonalViews().topLeft().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler());
		baseView.orthogonalViews().topLeft().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter());
		baseView.orthogonalViews().topRight().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler());
		baseView.orthogonalViews().topRight().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter());
		baseView.orthogonalViews().bottomLeft().viewer().addEventHandler(Event.ANY, this.getSourceSpecificViewerEventHandler());
		baseView.orthogonalViews().bottomLeft().viewer().addEventFilter(Event.ANY, this.getSourceSpecificViewerEventFilter());

		paneWithStatus.getPane().addEventHandler(Event.ANY, this.getSourceSpecificGlobalEventHandler());
		paneWithStatus.getPane().addEventFilter(Event.ANY, this.getSourceSpecificGlobalEventFilter());


		grabFocusOnMouseOver(
				baseView.orthogonalViews().topLeft().viewer(),
				baseView.orthogonalViews().topRight().viewer(),
				baseView.orthogonalViews().bottomLeft().viewer());

		this.openDatasetContextMenuHandler = addOpenDatasetContextMenuHandler(
				gateway,
				paneWithStatus.getPane(),
				baseView,
				keyTracker,
				projectDirectory,
				this.mouseTracker::getX,
				this.mouseTracker::getY,
				KeyCode.CONTROL,
				KeyCode.O);

		this.toggleMaximizeTopLeft = toggleMaximizeNode(orthogonalViews, gridConstraintsManager, 0, 0);
		this.toggleMaximizeTopRight = toggleMaximizeNode(orthogonalViews, gridConstraintsManager, 1, 0);
		this.toggleMaximizeBottomLeft = toggleMaximizeNode(orthogonalViews, gridConstraintsManager, 0, 1);

		viewerToTransforms.put(orthogonalViews.topLeft().viewer(), orthogonalViews.topLeft());
		viewerToTransforms.put(orthogonalViews.topRight().viewer(), orthogonalViews.topRight());
		viewerToTransforms.put(orthogonalViews.bottomLeft().viewer(), orthogonalViews.bottomLeft());

		multiBoxes = new MultiBoxOverlayRendererFX[] {
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().topLeft().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources()),
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().topRight().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources()),
				new MultiBoxOverlayRendererFX(
						baseView.orthogonalViews().bottomLeft().viewer()::getState,
						sourceInfo.trackSources(),
						sourceInfo.trackVisibleSources())
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
				toggleSideBar));

		baseView.allowedActionsProperty().addListener((obs, oldv, newv) -> paneWithStatus.getSideBar().setDisable(!newv.isAllowed(MenuActionType.SidePanel)));

		sourceInfo.trackSources().addListener(createSourcesInterpolationListener());

		EventFX.KEY_PRESSED(
				"toggle interpolation",
				e -> toggleInterpolation(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.I)).installInto(borderPane);
		EventFX.KEY_PRESSED(
				"cycle current source",
				e -> sourceInfo.incrementCurrentSourceIndex(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ChangeActiveSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.TAB)).installInto(borderPane);
		EventFX.KEY_PRESSED(
				"backwards cycle current source",
				e -> sourceInfo.decrementCurrentSourceIndex(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ChangeActiveSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.TAB)).installInto(borderPane);

		this.resizer = new GridResizer(gridConstraintsManager, 5, baseView.pane(), keyTracker);
		this.resizer.installInto(baseView.pane());

		final ObjectProperty<Source<?>> currentSource = sourceInfo.currentSourceProperty();

		final OrthogonalViewsValueDisplayListener vdl = new OrthogonalViewsValueDisplayListener(
				paneWithStatus::setCurrentValue,
				currentSource,
				s -> sourceInfo.getState(s).interpolationProperty().get());

		final OrthoViewCoordinateDisplayListener cdl = new OrthoViewCoordinateDisplayListener(
				paneWithStatus::setViewerCoordinateStatus,
				paneWithStatus::setWorldCoorinateStatus);

		onEnterOnExit.accept(new OnEnterOnExit(vdl.onEnter(), vdl.onExit()));
		onEnterOnExit.accept(new OnEnterOnExit(cdl.onEnter(), cdl.onExit()));

		onEnterOnExit.accept(new ARGBStreamSeedSetter(sourceInfo, keyTracker).onEnterOnExit());

		sourceInfo.trackSources().addListener(FitToInterval.fitToIntervalWhenSourceAddedListener(
				baseView.manager(),
				baseView.orthogonalViews().topLeft().viewer().widthProperty()::get));
		sourceInfo
				.trackSources()
				.addListener(new RunWhenFirstElementIsAdded<>(c -> baseView.viewer3D().setInitialTransformToInterval(sourceIntervalInWorldSpace(c.getAddedSubList().get(0)))));


		EventFX.KEY_PRESSED(
				"toggle maximize viewer",
				e -> toggleMaximizeTopLeft.toggleMaximizeViewer(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M)).installInto(orthogonalViews.topLeft().viewer());
		EventFX.KEY_PRESSED(
				"toggle maximize viewer",
				e -> toggleMaximizeTopRight.toggleMaximizeViewer(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M)).installInto(orthogonalViews.topRight().viewer());
		EventFX.KEY_PRESSED(
				"toggle maximize viewer",
				e -> toggleMaximizeBottomLeft.toggleMaximizeViewer(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M)).installInto(orthogonalViews.bottomLeft().viewer());

		EventFX.KEY_PRESSED(
				"toggle maximize viewer and orthoslice",
				e -> toggleMaximizeTopLeft.toggleMaximizeViewerAndOrthoslice(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT)).installInto(orthogonalViews.topLeft().viewer());

		EventFX.KEY_PRESSED(
				"toggle maximize viewer and orthoslice",
				e -> toggleMaximizeTopRight.toggleMaximizeViewerAndOrthoslice(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT)).installInto(orthogonalViews.topRight().viewer());

		EventFX.KEY_PRESSED(
				"toggle maximize viewer and orthoslice",
				e -> toggleMaximizeBottomLeft.toggleMaximizeViewerAndOrthoslice(),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.ToggleMaximizeViewer) && keyTracker.areOnlyTheseKeysDown(KeyCode.M, KeyCode.SHIFT)).installInto(orthogonalViews.bottomLeft().viewer());


		final CurrentSourceVisibilityToggle csv = new CurrentSourceVisibilityToggle(sourceInfo.currentState());
		EventFX.KEY_PRESSED(
				"toggle visibility",
				e -> csv.toggleIsVisible(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.V)).installInto(borderPane);

		final ShowOnlySelectedInStreamToggle sosist = new ShowOnlySelectedInStreamToggle(
				sourceInfo.currentState()::get,
				sourceInfo.removedSourcesTracker());
		EventFX.KEY_PRESSED(
				"toggle non-selected labels visibility",
				e -> sosist.toggleNonSelectionVisibility(),
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.SHIFT, KeyCode.V)).installInto(borderPane);


		final MeshesGroupContextMenu contextMenuFactory = new MeshesGroupContextMenu(baseView.manager());
		final ObjectProperty<ContextMenu> contextMenuProperty = new SimpleObjectProperty<>();
		final Runnable hideContextMenu = () -> {
			if (contextMenuProperty.get() != null) {
				contextMenuProperty.get().hide();
				contextMenuProperty.set(null);
			}
		};
		baseView.viewer3D().addEventHandler(
				MouseEvent.MOUSE_CLICKED,
				e -> {
					LOG.debug("Handling event {}", e);
					if (baseView.allowedActionsProperty().get().isAllowed(MenuActionType.OrthoslicesContextMenu) &&
							MouseButton.SECONDARY.equals(e.getButton()) &&
							e.getClickCount() == 1 &&
							!mouseTracker.isDragging())
					{
						LOG.debug("Check passed for event {}", e);
						e.consume();
						final PickResult pickResult = e.getPickResult();
						if (pickResult.getIntersectedNode() != null) {
							final Point3D pt = pickResult.getIntersectedPoint();
							final ContextMenu menu = contextMenuFactory.createMenu(new double[] {pt.getX(), pt.getY(), pt.getZ()});
							menu.show(baseView.viewer3D(), e.getScreenX(), e.getScreenY());
							contextMenuProperty.set(menu);
						} else {
							hideContextMenu.run();
						}
					} else {
						hideContextMenu.run();
					}
				}
			);
		// hide the context menu when clicked outside the meshes
		baseView.viewer3D().addEventHandler(
				MouseEvent.MOUSE_CLICKED,
				e -> hideContextMenu.run()
			);


		EventFX.KEY_PRESSED(
				"Create new label dataset",
				e -> CreateDatasetHandler.createAndAddNewLabelDataset(
						baseView,
						projectDirectory,
						Exceptions.handler(Paintera2.Constants.NAME, "Unable to create new Dataset"),
						baseView.sourceInfo().currentSourceProperty().get()),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.CreateNewLabelSource) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.SHIFT, KeyCode.N)).installInto(paneWithStatus.getPane());

		this.baseView.orthogonalViews().topLeft().viewer().addTransformListener(scaleBarOverlays.get(0));
		this.baseView.orthogonalViews().topLeft().viewer().getDisplay().addOverlayRenderer(scaleBarOverlays.get(0));
		this.baseView.orthogonalViews().topRight().viewer().addTransformListener(scaleBarOverlays.get(1));
		this.baseView.orthogonalViews().topRight().viewer().getDisplay().addOverlayRenderer(scaleBarOverlays.get(1));
		this.baseView.orthogonalViews().bottomLeft().viewer().addTransformListener(scaleBarOverlays.get(2));
		this.baseView.orthogonalViews().bottomLeft().viewer().getDisplay().addOverlayRenderer(scaleBarOverlays.get(2));
		scaleBarConfig.getChange().addListener(obs -> this.baseView.orthogonalViews().applyToAll(vp -> vp.getDisplay().drawOverlays()));

		final KeyCodeCombination addBookmarkKeyCode = new KeyCodeCombination(KeyCode.B);
		final KeyCodeCombination addBookmarkWithCommentKeyCode = new KeyCodeCombination(KeyCode.B, KeyCombination.SHIFT_DOWN);
		final KeyCodeCombination applyBookmarkKeyCode = new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN);
		paneWithStatus.getPane().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			if (!baseView.allowedActionsProperty().get().isAllowed(NavigationActionType.Bookmark))
				return;
			if (addBookmarkKeyCode.match(e)) {
				e.consume();
				final AffineTransform3D globalTransform = new AffineTransform3D();
				baseView.manager().getTransform(globalTransform);
				final Affine viewer3DTransform = new Affine();
				baseView.viewer3D().getAffine(viewer3DTransform);
				bookmarkConfig.addBookmark(new BookmarkConfig.Bookmark(globalTransform, viewer3DTransform, null));
			} else if (addBookmarkWithCommentKeyCode.match(e)) {
				e.consume();
				final AffineTransform3D globalTransform = new AffineTransform3D();
				baseView.manager().getTransform(globalTransform);
				final Affine viewer3DTransform = new Affine();
				baseView.viewer3D().getAffine(viewer3DTransform);
				paneWithStatus.bookmarkConfigNode().requestAddNewBookmark(globalTransform, viewer3DTransform);
			} else if (applyBookmarkKeyCode.match(e)) {
				e.consume();
				new BookmarkSelectionDialog(bookmarkConfig.getUnmodifiableBookmarks())
						.showAndWaitForBookmark()
						.ifPresent(bm -> {
							baseView.manager().setTransform(bm.getGlobalTransformCopy(), bookmarkConfig.getTransitionTime());
							baseView.viewer3D().setAffine(bm.getViewer3DTransformCopy(), bookmarkConfig.getTransitionTime());
						});
			}
		});

	}

	private final Map<ViewerPanelFX, ViewerAndTransforms> viewerToTransforms = new HashMap<>();

	public static DisplayTransformUpdateOnResize[] updateDisplayTransformOnResize(
			final OrthogonalViews<?> views,
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
				() -> focusTL.get() ? tl : focusTR.get() ? tr : focusBL.get() ? bl : null,
				focusTL,
				focusTR,
				focusBL);

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
		if (globalInterpolationProperty.get() != null)
		{
			globalInterpolationProperty.set(globalInterpolationProperty.get().equals(Interpolation.NLINEAR) ? Interpolation.NEARESTNEIGHBOR : Interpolation.NLINEAR);
			baseView.orthogonalViews().requestRepaint();
		}
	}

	private InvalidationListener createSourcesInterpolationListener()
	{
		return obs ->
		{
			if (globalInterpolationProperty.get() == null && !sourceInfo.trackSources().isEmpty())
			{
				// initially set the global interpolation state based on source interpolation
				final Source<?> source = sourceInfo.trackSources().iterator().next();
				final SourceState<?, ?> sourceState = sourceInfo.getState(source);
				globalInterpolationProperty.set(sourceState.interpolationProperty().get());
			}

			// bind all source interpolation states to the global state
			for (final Source<?> source : sourceInfo.trackSources())
			{
				final SourceState<?, ?> sourceState = sourceInfo.getState(source);
				sourceState.interpolationProperty().bind(globalInterpolationProperty);
			}
		};
	}

	private static Interval sourceIntervalInWorldSpace(final Source<?> source)
	{
		final double[] min = Arrays
				.stream(Intervals.minAsLongArray(source.getSource(0, 0)))
				.asDoubleStream()
				.toArray();
		final double[] max = Arrays
				.stream(Intervals.maxAsLongArray(source.getSource(0, 0)))
				.asDoubleStream()
				.toArray();
		final AffineTransform3D tf = new AffineTransform3D();
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

	public static EventHandler<KeyEvent> addOpenDatasetContextMenuHandler(
			final PainteraGateway gateway,
			final Node target,
			final PainteraBaseView baseView,
			final KeyTracker keyTracker,
			final Supplier<String> projectDirectory,
			final DoubleSupplier currentMouseX,
			final DoubleSupplier currentMouseY,
			final KeyCode... triggers)
	{

		assert triggers.length > 0;

		final EventHandler<KeyEvent> handler = OpenDialogMenu.keyPressedHandler(
				gateway,
				target,
				exception -> Exceptions.exceptionAlert(Paintera2.Constants.NAME, "Unable to show open dataset menu", exception),
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.AddSource) && keyTracker.areOnlyTheseKeysDown(triggers),
				"Open dataset",
				baseView,
				projectDirectory,
				currentMouseX,
				currentMouseY);

		target.addEventHandler(KeyEvent.KEY_PRESSED, handler);
		return handler;
	}

	public static ToggleMaximize toggleMaximizeNode(
			final OrthogonalViews<? extends Node> orthogonalViews,
			final GridConstraintsManager manager,
			final int column,
			final int row)
	{
		return new ToggleMaximize(
				orthogonalViews,
				manager,
				MaximizedColumn.fromIndex(column),
				MaximizedRow.fromIndex(row));
	}

	public Navigation navigation()
	{
		return this.navigation;
	}

	public ScaleBarOverlayConfig scaleBarConfig() {
		return this.scaleBarConfig;
	}

	public BookmarkConfig bookmarkConfig() {
		return this.bookmarkConfig;
	}

}
