package org.janelia.saalfeldlab.paintera;

import bdv.cache.SharedQueue;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.janelia.saalfeldlab.bdv.fx.viewer.render.PainterThreadFx;
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
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
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
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState;
import org.janelia.saalfeldlab.paintera.state.label.RaiBackendLabel;
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawState;
import org.janelia.saalfeldlab.paintera.state.raw.RaiBackendRaw;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.janelia.saalfeldlab.util.concurrent.HashPriorityQueueBasedTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

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


	public final ObservableObjectValue<ViewerAndTransforms> currentFocusHolder;
	private final ReadOnlyObjectWrapper<ViewerAndTransforms> _lastFocusHolder;
	public final ReadOnlyObjectProperty<ViewerAndTransforms> lastFocusHolder;


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

	private final ExecutorService propagationQueue;

	{
		final AtomicInteger count = new AtomicInteger();
		final ForkJoinPool.ForkJoinWorkerThreadFactory factory = pool -> {
			final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
			worker.setDaemon(true);
			worker.setPriority(4);
			worker.setName("propagation-queue-" + count.getAndIncrement());
			return worker;
		};

		propagationQueue = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 2), factory, (thread, throwable) -> throwable.printStackTrace(), false);

	}

	private final SharedQueue sharedQueue;

	private KeyAndMouseConfig keyAndMouseBindings;

	private final SimpleObjectProperty<ControlMode> activeModeProperty = new SimpleObjectProperty<>();

	public final ObservableMap<Object, ObservableBooleanValue> disabledPropertyBindings = FXCollections.synchronizedObservableMap(FXCollections.observableHashMap());

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
				.numRenderingThreads(Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
		this.views = new OrthogonalViews<>(
				manager,
				this.sharedQueue,
				this.viewerOptions,
				viewer3D,
				source -> Optional.ofNullable(sourceInfo.getState(source)).map(SourceState::interpolationProperty).map(ObjectProperty::get)
						.orElse(Interpolation.NLINEAR));

		this.currentFocusHolder = Bindings.createObjectBinding(
				() -> views.viewerAndTransforms().stream()
						.filter(it -> it.viewer().focusedProperty().get())
						.findFirst()
						.orElse(null),
				views.views().stream().map(Node::focusedProperty).toArray(Observable[]::new)
		);

		_lastFocusHolder = new ReadOnlyObjectWrapper<>(views.getTopLeft());
		lastFocusHolder = _lastFocusHolder.getReadOnlyProperty();
		currentFocusHolder.subscribe( (prev, cur) -> {
			if (cur != null) {
				_lastFocusHolder.set(cur);
			}
		});

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
					() -> Arrays.stream(disabledPropertyBindings.values().toArray(ObservableBooleanValue[]::new))
							.map(ObservableBooleanValue::get)
							.reduce(Boolean::logicalOr)
							.orElse(false),
					disabledPropertyBindings.values().toArray(new ObservableBooleanValue[]{}));
			isDisabledProperty.bind(isDisabledBinding);
		});

		activeModeProperty.set(AppControlMode.INSTANCE);


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
		LOG.debug("Meshes group={}", viewer3D.getMeshesGroup());
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
	 * Convenience method to add an array of {@link RandomAccessibleInterval} as multiscale {@link ConnectomicsRawState}.
	 * If only a single scale is provided, the resulting {@link ConnectomicsRawState} will be single scale.
	 *
	 * @param data       array of input data, each should be a scale level, mapping to the resolution and offset
	 * @param resolution array of voxel size per scale
	 * @param offset     array of offset in global coordinates per scale
	 * @param min        minimum value of display range
	 * @param max        maximum value of display range
	 * @param name       name for source
	 * @param <D>        Data type of {@code state}
	 * @param <T>        Viewer type of {@code state}
	 * @return the {@link ConnectomicsRawState} that was built from the inputs and added to the viewer
	 */
	public <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>> ConnectomicsRawState<D, T> addMultiscaleConnectomicsRawSource(
			final RandomAccessibleInterval<D>[] data,
			final double[][] resolution,
			final double[][] offset,
			final double min,
			final double max,
			final String name) {

		final ConnectomicsRawState<D, T> state = new ConnectomicsRawState<D, T>(
				new RaiBackendRaw<>(data, resolution, offset, "test"),
				getQueue(),
				getQueue().getNumPriorities() - 1,
				name
		);
		InvokeOnJavaFXApplicationThread.invoke(() -> {
			addState(state);
			return null;
		});
		state.converter().setMin(min);
		state.converter().setMax(max);
		return state;
	}

	/**
	 * Convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link ConnectomicsRawState}
	 *
	 * @param data       input data
	 * @param resolution voxel size
	 * @param offset     offset in global coordinates
	 * @param min        minimum value of display range
	 * @param max        maximum value of display range
	 * @param name       name for source
	 * @param <D>        Data type of {@code state}
	 * @param <T>        Viewer type of {@code state}
	 * @return the {@link ConnectomicsRawState} that was built from the inputs and added to the viewer
	 */
	public <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>> ConnectomicsRawState<D, T> addConnectomicsRawSource(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final double min,
			final double max,
			final String name) {

		return addMultiscaleConnectomicsRawSource(
				new RandomAccessibleInterval[]{data},
				new double[][]{resolution},
				new double[][]{offset},
				min,
				max,
				name
		);
	}

	/**
	 * Convenience method to add multiple {@link RandomAccessibleInterval} as multiscale {@link ConnectomicsLabelState}.
	 * If only a single scale is provided, the resulting {@link ConnectomicsLabelState} will be single scale.
	 *
	 * @param data             array of input data, each should be a scale level, mapping to the resolution and offset
	 * @param resolution       array of voxel size per scale
	 * @param offset           array of offset in global coordinates per scale
	 * @param maxId            the maximum value in {@code data}
	 * @param name             name for source
	 * @param labelBlockLookup used for rendering the mesh blocks
	 * @param <D>              Data type of {@code state}
	 * @param <T>              Viewer type of {@code state}
	 * @return the {@link ConnectomicsLabelState} that was built from the inputs and added to the viewer
	 */
	public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & Type<T>> ConnectomicsLabelState<D, T>
	addConnectomicsLabelSource(
			final RandomAccessibleInterval<D>[] data,
			final double[][] resolution,
			final double[][] offset,
			final long maxId,
			final String name,
			LabelBlockLookup labelBlockLookup) {

		final RaiBackendLabel<D, T> backend = new RaiBackendLabel<>(name, data, resolution, offset, maxId);
		final var state = new ConnectomicsLabelState<>(
				backend,
				viewer3D().getMeshesGroup(),
				viewer3D().getViewFrustumProperty(),
				viewer3D().getEyeToWorldTransformProperty(),
				meshManagerExecutorService,
				meshWorkerExecutorService,
				getQueue(),
				getQueue().getNumPriorities() - 1,
				name,
				labelBlockLookup
		);
		InvokeOnJavaFXApplicationThread.invoke(() -> {
			addState(state);
			return null;
		});
		return state;
	}

	/**
	 * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link ConnectomicsLabelState}
	 *
	 * @param data             input data
	 * @param resolution       voxel size
	 * @param offset           offset in global coordinates
	 * @param maxId            the maximum value in {@code data}
	 * @param name             name for source
	 * @param labelBlockLookup used for rendering the mesh blocks
	 * @param <D>              Data type of {@code state}
	 * @param <T>              Viewer type of {@code state}
	 * @return the {@link ConnectomicsLabelState} that was built from the inputs and added to the viewer
	 */
	public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & Type<T>> ConnectomicsLabelState<D, T>
	addConnectomicsLabelSource(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final long maxId,
			final String name,
			LabelBlockLookup labelBlockLookup) {

		return addConnectomicsLabelSource(
				new RandomAccessibleInterval[]{data},
				new double[][]{resolution},
				new double[][]{offset},
				maxId,
				name,
				labelBlockLookup
		);
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
		// exit current mode, and don't active another when shutting down;
		final ControlMode currentMode = activeModeProperty.get();
		if (currentMode != null) currentMode.exit();
		activeModeProperty.set(null);

		LOG.debug("Notifying sources about upcoming shutdown");
		this.sourceInfo.trackSources().forEach(s -> this.sourceInfo.getState(s).onShutdown(this));

		LOG.debug("Stopping everything");
		this.generalPurposeExecutorService.shutdown();
		this.meshManagerExecutorService.shutdown();
		this.meshWorkerExecutorService.shutdown();
		this.paintQueue.shutdown();
		this.propagationQueue.shutdown();

		this.orthogonalViews().stop();
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
	 * TODO like the {@link PainterThreadFx} for rendering
	 */
	public ExecutorService getMeshManagerExecutorService() {

		return meshManagerExecutorService;
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
