package org.janelia.saalfeldlab.paintera;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig;
import org.janelia.saalfeldlab.paintera.control.actions.ActionType;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions;
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActionsProperty;
import org.janelia.saalfeldlab.paintera.control.modes.AppControlMode;
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode;
import org.janelia.saalfeldlab.paintera.control.modes.NavigationControlMode;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.MeshWorkerPriority;
import org.janelia.saalfeldlab.paintera.state.*;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contains all the things necessary to build a Paintera UI, most importantly:
 * <p><ul>
 * <li>{@link OrthogonalViews 2D cross-section viewers}</li>
 * <li>{@link Viewer3DFX 3D viewer}</li>
 * <li>{@link SourceInfo source state management}</li>
 * <li>{@link ExecutorService thread management} for number crunching</li>
 * <li>{@link AllowedActions UI mode}</li>
 * </ul><p>
 */
public class PainteraBaseView {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int DEFAULT_MAX_NUM_CACHE_ENTRIES = 1000;

  // set this absurdly high
  private static final int MAX_NUM_MIPMAP_LEVELS = 100;

  private final SourceInfo sourceInfo = new SourceInfo();

  private final GlobalTransformManager manager = new GlobalTransformManager();

  private final ViewerOptions viewerOptions;

  private final Viewer3DFX viewer3D = new Viewer3DFX(1, 1);

  private final OrthogonalViews<Viewer3DFX> views;

  public final ObservableObjectValue<OrthogonalViews.ViewerAndTransforms> currentFocusHolder;
  public final ObservableObjectValue<OrthogonalViews.ViewerAndTransforms> lastFocusHolder;

  private final AllowedActionsProperty allowedActionsProperty;

  private final SimpleBooleanProperty isDisabledProperty = new SimpleBooleanProperty(false);

  private final ObservableList<SourceAndConverter<?>> visibleSourcesAndConverters = sourceInfo
		  .trackVisibleSourcesAndConverters();

  private final ListChangeListener<SourceAndConverter<?>> vsacUpdate;

  private final ExecutorService generalPurposeExecutorService = Executors.newFixedThreadPool(
		  3,
		  new NamedThreadFactory("paintera-thread-%d", true));

  private final ExecutorService meshManagerExecutorService = Executors.newFixedThreadPool(
		  3,
		  new NamedThreadFactory("paintera-mesh-manager-%d", true));

  private final HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> meshWorkerExecutorService = new HashPriorityQueueBasedTaskExecutor<>(
		  Comparator.naturalOrder(),
		  Math.min(10, Runtime.getRuntime().availableProcessors() - 1),
		  new NamedThreadFactory("paintera-mesh-worker-%d", true, Thread.MIN_PRIORITY));

  private final ExecutorService paintQueue = Executors.newFixedThreadPool(1);

  private final ExecutorService propagationQueue = Executors.newFixedThreadPool(1);

  private final SharedQueue sharedQueue;

  private KeyAndMouseConfig keyAndMouseBindings;

  private final SimpleObjectProperty<ControlMode> activeModeProperty = new SimpleObjectProperty<>();

  public final ObservableMap<Object, ObservableBooleanValue> disabledPropertyBindings = FXCollections.observableHashMap();

  /**
   * delegates to {@link #PainteraBaseView(int, ViewerOptions, KeyAndMouseConfig) {@code PainteraBaseView(numFetcherThreads, ViewerOptions.options())}}
   */
  public PainteraBaseView(
		  final int numFetcherThreads,
		  final KeyAndMouseConfig keyAndMouseBindings) {

	this(numFetcherThreads, ViewerOptions.options(), keyAndMouseBindings);
  }

  /**
   * @param numFetcherThreads number of threads used for {@link net.imglib2.cache.queue.FetcherThreads}
   * @param viewerOptions     options passed down to {@link OrthogonalViews viewers}
   */
  public PainteraBaseView(
		  final int numFetcherThreads,
		  final ViewerOptions viewerOptions,
		  final KeyAndMouseConfig keyAndMouseBindings) {

	super();
	this.sharedQueue = new SharedQueue(numFetcherThreads, 50);
	this.keyAndMouseBindings = keyAndMouseBindings;
	this.viewerOptions = viewerOptions
			.accumulateProjectorFactory(new CompositeProjectorPreMultiply.CompositeProjectorFactory(sourceInfo.composites()))
			.numRenderingThreads(Math.min(3, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)));
	this.views = new OrthogonalViews<>(
			manager,
			this.sharedQueue,
			this.viewerOptions,
			viewer3D,
			source -> Optional.ofNullable(sourceInfo.getState(source)).map(SourceState::interpolationProperty).map(ObjectProperty::get).orElse(Interpolation.NLINEAR));

	this.currentFocusHolder = Bindings.createObjectBinding(
			() -> views.viewerAndTransforms().stream()
					.filter(it -> it.viewer().focusedProperty().get())
					.findFirst()
					.orElse(null),
			views.views().stream().map(Node::focusedProperty).toArray(Observable[]::new)
	);

	final var previousFocusHolder = new SimpleObjectProperty<>(currentFocusHolder.get());
	this.lastFocusHolder = Bindings.createObjectBinding(() -> {
		final OrthogonalViews.ViewerAndTransforms focusedViewer = currentFocusHolder.get();
		if (focusedViewer != null) {
			previousFocusHolder.set(focusedViewer);
		}
		return previousFocusHolder.get();
	}, currentFocusHolder);


	activeModeProperty.addListener((obs, oldv, newv) -> {
	  if (oldv != newv) {
		if (oldv != null)
		  oldv.exit();
		if (newv != null)
		  newv.enter();
	  }
	});

	disabledPropertyBindings.addListener((MapChangeListener<Object, ObservableBooleanValue>)change -> {
	  isDisabledProperty.unbind();

	  final var isDisabledBinding = Bindings.createBooleanBinding(
		  () -> disabledPropertyBindings.values().stream()
			  .map(ObservableBooleanValue::get)
			  .reduce(Boolean::logicalOr)
			  .orElse(false),
		  disabledPropertyBindings.values().toArray(new ObservableBooleanValue[]{}));
	  isDisabledProperty.bind(isDisabledBinding);
	});

	activeModeProperty.set(AppControlMode.INSTANCE);

	sourceInfo().currentSourceProperty().addListener((obs, oldv, newv) ->
			Optional.ofNullable(newv)
					.map(sourceInfo::getState)
					.ifPresent(state -> activeModeProperty.set(state.getDefaultMode()))
	);

	this.allowedActionsProperty = new AllowedActionsProperty(getNode().cursorProperty());

	this.allowedActionsProperty.bind(Bindings.createObjectBinding(() -> {
	  final var activeMode = activeModeProperty.get();
	  if (activeMode != null) {
		return activeMode.getAllowedActions();
	  } else {
		return new AllowedActions.AllowedActionsBuilder().create();
	  }
	}, activeModeProperty));

	this.isDisabledProperty.addListener((obs, wasDisabled, isDisabled) -> {
	  if (isDisabled) {
		allowedActionsProperty.disable();
	  } else {
		allowedActionsProperty.enable();
	  }
	});
	this.vsacUpdate = change -> views.setAllSources(visibleSourcesAndConverters);
	visibleSourcesAndConverters.addListener(vsacUpdate);
	LOG.debug("Meshes group={}", viewer3D.meshesGroup());
  }

  public ObservableValue<ControlMode> getActiveModeProperty() {

	return activeModeProperty;
  }

  public void changeMode(ControlMode mode) {

	activeModeProperty.set(mode);
  }

  /**
   * @return {@link OrthogonalViews orthogonal viewers} ui element and management
   */
  public OrthogonalViews<Viewer3DFX> orthogonalViews() {

	return this.views;
  }

  /**
   * @return {@link Viewer3DFX 3D viewer}
   */
  public Viewer3DFX viewer3D() {

	return this.viewer3D;
  }

  /**
   * @return {@link SourceInfo source state management}
   */
  public SourceInfo sourceInfo() {

	return this.sourceInfo;
  }

  /**
   * @return {@link Pane} that can be added to a JavaFX scene graph
   */
  public Node getNode() {

	return orthogonalViews().pane();
  }

  /**
   * @return {@link GlobalTransformManager} that manages shared transforms of {@link OrthogonalViews viewers}
   */
  @Nonnull
  public GlobalTransformManager manager() {

	return this.manager;
  }

  /**
   * @return {@link AllowedActions} that describe the user interface in the current application mode
   */
  public AllowedActionsProperty allowedActionsProperty() {

	return this.allowedActionsProperty;
  }

  /**
   * @return {@link javafx.beans.property.BooleanProperty} that can be used to disable or enable User Interaction
   */
  public BooleanProperty isDisabledProperty() {

	return this.isDisabledProperty;
  }

  /**
   * Set application to default tool mode.
   */
  public void setDefaultToolMode() {

	this.activeModeProperty.set(NavigationControlMode.INSTANCE);
  }

  /**
   * Add a source and state to the viewer
   *
   * @param state will delegate, if appropriate {@link SourceState state} to {@link #addGenericState(SourceState)}.
	 *
   * @param <D>   Data type of {@code state}
   * @param <T>   Viewer type of {@code state}
   */
  public <D, T> void addState(final SourceState<D, T> state) {

	addGenericState(state);
	state.onAdd(this);
	keyAndMouseBindings.getConfigFor(state);
  }

  /**
   * add a generic state without any further information about the kind of state
   * <p>
   * Changes to {@link SourceState#compositeProperty()} trigger
   * {@link OrthogonalViews#requestRepaint() a request for repaint} of the underlying viewers.
   * <p>
   * If {@code state} holds a {@link MaskedSource}, {@link MaskedSource#showCanvasOverBackgroundProperty()}
   * and {@link MaskedSource#currentCanvasDirectoryProperty()} trigger {@link OrthogonalViews#requestRepaint()}.
   *
   * @param state generic state
   * @param <D>   Data type of {@code state}
   * @param <T>   Viewer type of {@code state}
   */
  public <D, T> void addGenericState(final SourceState<D, T> state) {

	sourceInfo.addState(state);

	state.compositeProperty().addListener(obs -> orthogonalViews().requestRepaint());

	if (state.getDataSource() instanceof MaskedSource<?, ?>) {
	  final MaskedSource<?, ?> ms = ((MaskedSource<?, ?>)state.getDataSource());
	  ms.showCanvasOverBackgroundProperty().addListener(obs -> orthogonalViews().requestRepaint());
	  ms.currentCanvasDirectoryProperty().addListener(obs -> orthogonalViews().requestRepaint());
	}
  }

  /**
   * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link RawSourceState}
   *
   * @param data       input data
   * @param resolution voxel size
   * @param offset     offset in global coordinates
   * @param min        minimum value of display range
   * @param max        maximum value of display range
   * @param name       name for source
   * @param <D>        Data type of {@code state}
   * @param <T>        Viewer type of {@code state}
   * @return the {@link RawSourceState} that was built from the inputs and added to the viewer
   */
  public <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>> RawSourceState<D, T> addSingleScaleRawSource(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final double min,
		  final double max,
		  final String name) {

	final RawSourceState<D, T> state = RawSourceState.simpleSourceFromSingleRAI(data, resolution, offset, min, max,
			name);
	InvokeOnJavaFXApplicationThread.invoke(() -> addState(state));
	return state;
  }

  /**
   * Add {@link RawSourceState raw data}
   * <p>
   * delegates to {@link #addGenericState(SourceState)} and triggers {@link OrthogonalViews#requestRepaint()}
   * on changes to these properties:
   * <p><ul>
   * <li>{@link ARGBColorConverter#colorProperty()}</li>
   * <li>{@link ARGBColorConverter#minProperty()}</li>
   * <li>{@link ARGBColorConverter#maxProperty()}</li>
   * <li>{@link ARGBColorConverter#alphaProperty()}</li>
   * </ul><p>
   *
   * @param state input
   * @param <T>   Data type of {@code state}
   * @param <U>   Viewer type of {@code state}
   */
  @Deprecated
  public <T extends RealType<T>, U extends RealType<U>> void addRawSource(final RawSourceState<T, U> state) {

	LOG.debug("Adding raw state={}", state);
	addState(state);
  }

  /**
   * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link LabelSourceState}
   *
   * @param data       input data
   * @param resolution voxel size
   * @param offset     offset in global coordinates
   * @param maxId      the maximum value in {@code data}
   * @param name       name for source
   * @param <D>        Data type of {@code state}
   * @param <T>        Viewer type of {@code state}
   * @return the {@link LabelSourceState} that was built from the inputs and added to the viewer
   */
  public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>> LabelSourceState<D, T>
  addSingleScaleLabelSource(
		  final RandomAccessibleInterval<D> data,
		  final double[] resolution,
		  final double[] offset,
		  final long maxId,
		  final String name) {

	final LabelSourceState<D, T> state = LabelSourceState.simpleSourceFromSingleRAI(
			data,
			resolution,
			offset,
			maxId,
			name,
			viewer3D().meshesGroup(),
			viewer3D().viewFrustumProperty(),
			viewer3D().eyeToWorldTransformProperty(),
			meshManagerExecutorService,
			meshWorkerExecutorService);
	InvokeOnJavaFXApplicationThread.invoke(() -> addState(state));
	return state;
  }

  /**
   * Add {@link LabelSourceState raw data}
   * <p>
   * delegates to {@link #addState(SourceState)}
   *
   * @param state input
   * @param <D>   Data type of {@code state}
   * @param <T>   Viewer type of {@code state}
   */
  @Deprecated
  public <D extends IntegerType<D>, T extends Volatile<D> & Type<T>> void addLabelSource(final LabelSourceState<D, T> state) {

	LOG.debug("Adding label state={}", state);
	addState(state);
  }

  /**
   * Add {@link ChannelSourceState raw data}
   * <p>
   * delegates to {@link #addGenericState(SourceState)} and triggers {@link OrthogonalViews#requestRepaint()}
   * on changes to these properties:
   * <p><ul>
   * <li>{@link ARGBCompositeColorConverter#colorProperty(int)}</li>
   * <li>{@link ARGBCompositeColorConverter#minProperty(int)}</li>
   * <li>{@link ARGBCompositeColorConverter#maxProperty(int)}</li>
   * <li>{@link ARGBCompositeColorConverter#channelAlphaProperty(int)}</li>
   * <li>{@link ARGBCompositeColorConverter#alphaProperty()}</li>
   * </ul><p>
   *
   * @param state input
   * @param <D>   Data type of {@code state}
   * @param <T>   Viewer type of {@code state}
   * @param <CT>  Composite data type of {@code state}
   * @param <V>   Composite viewer type of {@code state}
   */
  @Deprecated
  public <
		  D extends RealType<D>,
		  T extends AbstractVolatileRealType<D, T>,
		  CT extends RealComposite<T>,
		  V extends Volatile<CT>> void addChannelSource(
		  final ChannelSourceState<D, T, CT, V> state) {

	LOG.debug("Adding channel state={}", state);
	addState(state);
	LOG.debug("Added channel state {}", state.nameProperty().get());
  }

  /**
   * @return {@link ExecutorService} for general purpose computations
   */
  public ExecutorService generalPurposeExecutorService() {

	return this.generalPurposeExecutorService;
  }

  /**
   * @return {@link ExecutorService} for painting related computations
   */
  public ExecutorService getPaintQueue() {

	return this.paintQueue;
  }

  /**
   * @return {@link ExecutorService} for down-/upsampling painted labels
   */
  public ExecutorService getPropagationQueue() {

	return this.propagationQueue;
  }

  /**
   * shut down {@link ExecutorService executors} and {@link Thread threads}.
   */
  public void stop() {
	// ensure that the application is in the default mode when the sources are shutting down
	setDefaultToolMode();

	LOG.debug("Notifying sources about upcoming shutdown");
	this.sourceInfo.trackSources().forEach(s -> this.sourceInfo.getState(s).onShutdown(this));

	LOG.debug("Stopping everything");
	this.generalPurposeExecutorService.shutdown();
	this.meshManagerExecutorService.shutdown();
	this.meshWorkerExecutorService.shutdown();
	this.paintQueue.shutdown();
	this.propagationQueue.shutdown();

	this.orthogonalViews().getTopLeft().viewer().stop();
	this.orthogonalViews().getTopRight().viewer().stop();
	this.orthogonalViews().getBottomLeft().viewer().stop();
  }

  /**
   * Determine a good number of fetcher threads.
   *
   * @return half of all available processor, but no more than eight and no less than 1.
   */
  public static int reasonableNumFetcherThreads() {

	return Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
  }

  /**
   * @return {@link ExecutorService} for managing mesh generation tasks
   * <p>
   * TODO this should probably be removed by a management thread for every single mesh manager
   * TODO like the {@link bdv.fx.viewer.render.PainterThread} for rendering
   */
  public ExecutorService getMeshManagerExecutorService() {

	return this.meshManagerExecutorService;
  }

  /**
   * @return {@link ExecutorService} for the heavy workload in mesh generation tasks
   */
  public HashPriorityQueueBasedTaskExecutor<MeshWorkerPriority> getMeshWorkerExecutorService() {

	return this.meshWorkerExecutorService;
  }

  public SharedQueue getQueue() {

	return this.sharedQueue;
  }

  public KeyAndMouseConfig getKeyAndMouseBindings() {

	return this.keyAndMouseBindings;
  }

  public void setKeyAndMouseBindings(final KeyAndMouseConfig bindings) {

	this.keyAndMouseBindings = bindings;
  }

  public boolean isActionAllowed(ActionType action) {

	return allowedActionsProperty.isAllowed(action);
  }
}
