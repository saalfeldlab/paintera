package org.janelia.saalfeldlab.paintera;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.layout.Pane;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.LoaderCache;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.cache.DiscoverableMemoryUsage;
import org.janelia.saalfeldlab.paintera.cache.Invalidate;
import org.janelia.saalfeldlab.paintera.cache.MemoryBoundedSoftRefLoaderCache;
import org.janelia.saalfeldlab.paintera.cache.global.GlobalCache;
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply;
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.NavigationConfig;
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigBase;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrderNotSupported;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.HasFragmentSegmentAssignments;
import org.janelia.saalfeldlab.paintera.state.HasHighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.state.HasLockedSegments;
import org.janelia.saalfeldlab.paintera.state.HasMeshes;
import org.janelia.saalfeldlab.paintera.state.HasSelectedIds;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contains all the things necessary to build a Paintera UI, most importantly:
 * <p><ul>
 * <li>{@link OrthogonalViews 2D cross-section viewers}</li>
 * <li>{@link Viewer3DFX 3D viewer}</li>
 * <li>{@link SourceInfo source state management}</li>
 * <li>{@link GlobalCache global cache management}</li>
 * <li>{@link ExecutorService thread management} for number crunching</li>
 * </ul><p>
 */
public class PainteraBaseView
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final int DEFAULT_MAX_NUM_CACHE_ENTRIES = 1000;

	// set this absurdly high
	private static final int MAX_NUM_MIPMAP_LEVELS = 100;

	private final SourceInfo sourceInfo = new SourceInfo();

	private final GlobalTransformManager manager = new GlobalTransformManager();

//	private final LoaderCache<GlobalCache.Key<?>, ?> globalBackingCache = new BoundedSoftRefLoaderCache<>(DEFAULT_MAX_NUM_CACHE_ENTRIES);

	// 1GB
	private final LoaderCache<GlobalCache.Key<?>, ?> globalBackingCache = MemoryBoundedSoftRefLoaderCache.withWeakRefs(Runtime.getRuntime().maxMemory(), DiscoverableMemoryUsage.memoryUsageFromDiscoveredFunctions());

	private final GlobalCache globalCache;

	private final ViewerOptions viewerOptions;

	private final Viewer3DFX viewer3D = new Viewer3DFX(1, 1);

	private final OrthogonalViews<Viewer3DFX> views;

	private final ObservableList<SourceAndConverter<?>> visibleSourcesAndConverters = sourceInfo
			.trackVisibleSourcesAndConverters();

	private final ListChangeListener<SourceAndConverter<?>> vsacUpdate;

	private final ExecutorService generalPurposeExecutorService = Executors.newFixedThreadPool(
			3,
			new NamedThreadFactory("paintera-thread-%d", true));

	private final ExecutorService meshManagerExecutorService = Executors.newFixedThreadPool(
			3,
			new NamedThreadFactory("paintera-mesh-manager-%d", true));

	private final ExecutorService meshWorkerExecutorService = Executors.newFixedThreadPool(
			10,
			new NamedThreadFactory("paintera-mesh-worker-%d", true));

	private final ExecutorService paintQueue = Executors.newFixedThreadPool(1);

	private final ExecutorService propagationQueue = Executors.newFixedThreadPool(1);

	/**
	 *
	 * delegates to {@link #PainteraBaseView(int, ViewerOptions) {@code PainteraBaseView(numFetcherThreads, ViewerOptions.options())}}
	 */
	public PainteraBaseView(final int numFetcherThreads)
	{
		this(numFetcherThreads, ViewerOptions.options());
	}

	/**
	 *
	 * @param numFetcherThreads number of threads used for {@link net.imglib2.cache.queue.FetcherThreads}
	 * @param viewerOptions options passed down to {@link OrthogonalViews viewers}
	 */
	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions)
	{
		super();
		this.globalCache = new GlobalCache(MAX_NUM_MIPMAP_LEVELS, numFetcherThreads, globalBackingCache, (Invalidate<GlobalCache.Key<?>>)globalBackingCache);
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory(new CompositeProjectorPreMultiply.CompositeProjectorFactory(sourceInfo
						.composites()))
				// .accumulateProjectorFactory( new
				// ClearingCompositeProjector.ClearingCompositeProjectorFactory<>(
				// sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads(Math.min(3, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)));
		this.views = new OrthogonalViews<>(
				manager,
				this.globalCache,
				this.viewerOptions,
				viewer3D,
				s -> Optional.ofNullable(sourceInfo.getState(s)).map(SourceState::interpolationProperty).map(ObjectProperty::get).orElse(Interpolation.NLINEAR),
				s -> Optional.ofNullable(sourceInfo.getState(s)).map(SourceState::getAxisOrder).orElse(null)
		);
		this.vsacUpdate = change -> views.setAllSources(visibleSourcesAndConverters);
		visibleSourcesAndConverters.addListener(vsacUpdate);
		LOG.debug("Meshes group={}", viewer3D.meshesGroup());
	}

	/**
	 *
	 * @return {@link OrthogonalViews orthogonal viewers} ui element and management
	 */
	public OrthogonalViews<Viewer3DFX> orthogonalViews()
	{
		return this.views;
	}

	/**
	 *
	 * @return {@link Viewer3DFX 3D viewer}
	 */
	public Viewer3DFX viewer3D()
	{
		return this.viewer3D;
	}

	/**
	 *
	 * @return {@link SourceInfo source state management}
	 */
	public SourceInfo sourceInfo()
	{
		return this.sourceInfo;
	}

	/**
	 *
	 * @return {@link Pane} that can be added to a JavaFX scene graph
	 */
	public Pane pane()
	{
		return orthogonalViews().pane();
	}

	/**
	 *
	 * @return {@link GlobalTransformManager} that manages shared transforms of {@link OrthogonalViews viewers}
	 */
	public GlobalTransformManager manager()
	{
		return this.manager;
	}

	/**
	 *
	 * Add a source and state to the viewer
	 *
	 * @param state will delegate, if appropriate {@link SourceState state}, to
	 *              <p><ul>
	 *              <li>{@link #addLabelSource(LabelSourceState)} }</li>
	 *              <li>{@link #addRawSource(RawSourceState)}</li>
	 *              <li>{@link #addChannelSource(ChannelSourceState)}</li>
	 *              </ul><p>
	 *              or call {@link #addGenericState(SourceState)} otherwise.
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 */
	@SuppressWarnings("unchecked")
	public <D, T> void addState(final SourceState<D, T> state)
	{
		addGenericState(state);
		state.onAdd(this);
	}

	/**
	 * add a generic state without any further information about the kind of state
	 *
	 * Changes to {@link SourceState#compositeProperty()} and {@link SourceState#axisOrderProperty()} trigger
	 * {@link OrthogonalViews#requestRepaint() a request for repaint} of the underlying viewers.
	 *
	 * If {@code state} holds a {@link MaskedSource}, {@link MaskedSource#showCanvasOverBackgroundProperty()}
	 * and {@link MaskedSource#currentCanvasDirectoryProperty()} trigger {@link OrthogonalViews#requestRepaint()}.
	 *
	 * @param state generic state
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 */
	public <D, T> void addGenericState(final SourceState<D, T> state)
	{
		sourceInfo.addState(state);

		state.compositeProperty().addListener(obs -> orthogonalViews().requestRepaint());
		state.axisOrderProperty().addListener(obs -> orthogonalViews().requestRepaint());

		if (state.getDataSource() instanceof MaskedSource<?, ?>) {
			final MaskedSource<?, ?> ms = ((MaskedSource<?, ?>) state.getDataSource());
			ms.showCanvasOverBackgroundProperty().addListener(obs -> orthogonalViews().requestRepaint());
			ms.currentCanvasDirectoryProperty().addListener(obs -> orthogonalViews().requestRepaint());
		}
	}

	/**
	 * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link RawSourceState}
	 *
	 * @param data input data
	 * @param resolution voxel size
	 * @param offset offset in global coordinates
	 * @param min minimum value of display range
	 * @param max maximum value of display range
	 * @param name name for source
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 * @return the {@link RawSourceState} that was built from the inputs and added to the viewer
	 */
	public <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>> RawSourceState<D, T> addSingleScaleRawSource(
			final RandomAccessibleInterval<D> data,
			double[] resolution,
			double[] offset,
			double min,
			String name,
			double max) throws AxisOrderNotSupported {
		RawSourceState<D, T> state = RawSourceState.simpleSourceFromSingleRAI(data, resolution, offset, min, max,
				name);
		InvokeOnJavaFXApplicationThread.invoke(() -> addRawSource(state));
		return state;
	}

	/**
	 *
	 * Add {@link RawSourceState raw data}
	 *
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
	 * @param <T> Data type of {@code state}
	 * @param <U> Viewer type of {@code state}
	 */
	@Deprecated
	public <T extends RealType<T>, U extends RealType<U>> void addRawSource(final RawSourceState<T, U> state)
	{
		LOG.debug("Adding raw state={}", state);
		addState(state);
	}


	/**
	 * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link LabelSourceState}
	 *
	 * @param data input data
	 * @param resolution voxel size
	 * @param offset offset in global coordinates
	 * @param maxId the maximum value in {@code data}
	 * @param name name for source
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 * @return the {@link LabelSourceState} that was built from the inputs and added to the viewer
	 */
	public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>> LabelSourceState<D, T>
	addSingleScaleLabelSource(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final long maxId,
			final String name) throws AxisOrderNotSupported {
		return addSingleScaleLabelSource(data, resolution, offset, AxisOrder.XYZ, maxId, name);
	}

	/**
	 * convenience method to add a single {@link RandomAccessibleInterval} as single scale level {@link LabelSourceState}
	 *
	 * @param data input data
	 * @param resolution voxel size
	 * @param offset offset in global coordinates
	 * @param axisOrder axis permutation
	 * @param maxId the maximum value in {@code data}
	 * @param name name for source
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 * @return the {@link LabelSourceState} that was built from the inputs and added to the viewer
	 */
	public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>> LabelSourceState<D, T>
	addSingleScaleLabelSource(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final AxisOrder axisOrder,
			final long maxId,
			final String name) throws AxisOrderNotSupported {
		LabelSourceState<D, T> state = LabelSourceState.simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				axisOrder,
				maxId,
				name,
				getGlobalCache(),
				viewer3D().meshesGroup(),
				meshManagerExecutorService,
				meshWorkerExecutorService);
		state.setAxisOrder(axisOrder);
		InvokeOnJavaFXApplicationThread.invoke(() -> addLabelSource(state));
		return state;
	}

	/**
	 *
	 * Add {@link LabelSourceState raw data}
	 *
	 * delegates to {@link #addGenericState(SourceState)} and triggers {@link OrthogonalViews#requestRepaint()}
	 * on changes to these properties:
	 * <p><ul>
	 * <li>{@link AbstractHighlightingARGBStream}</li>
	 * <li>{@link LabelSourceState#assignment()}</li>
	 * <li>{@link LabelSourceState#selectedIds()}</li>
	 * <li>{@link LabelSourceState#lockedSegments()}</li>
	 * </ul><p>
	 *
	 * @param state input
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 */
	@Deprecated
	public <D extends IntegerType<D>, T extends Type<T>> void addLabelSource(final LabelSourceState<D, T> state)
	{
		LOG.debug("Adding label state={}", state);
		addState(state);
	}

	/**
	 *
	 * Add {@link ChannelSourceState raw data}
	 *
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
	 * @param <D> Data type of {@code state}
	 * @param <T> Viewer type of {@code state}
	 * @param <CT> Composite data type of {@code state}
	 * @param <V> Composite viewer type of {@code state}
	 */
	@Deprecated
	public <
			D extends RealType<D>,
			T extends AbstractVolatileRealType<D, T>,
			CT extends RealComposite<T>,
			V extends Volatile<CT>> void addChannelSource(
			final ChannelSourceState<D, T, CT, V> state)
	{
		LOG.debug("Adding channel state={}", state);
		addState(state);
		LOG.debug("Added channel state {}", state.nameProperty().get());
	}

	/**
	 *
	 * @return {@link ExecutorService} for general purpose computations
	 */
	public ExecutorService generalPurposeExecutorService()
	{
		return this.generalPurposeExecutorService;
	}

	/**
	 *
	 * @return {@link ExecutorService} for painting related computations
	 */
	public ExecutorService getPaintQueue()
	{
		return this.paintQueue;
	}

	/**
	 *
	 * @return {@link ExecutorService} for down-/upsampling painted labels
	 */
	public ExecutorService getPropagationQueue()
	{
		return this.propagationQueue;
	}

	/**
	 * shut down {@link ExecutorService executors} and {@link Thread threads}.
	 * TODO this can probably be removed, because everything should be daemon threads!
	 */
	public void stop()
	{
		LOG.debug("Stopping everything");
		this.generalPurposeExecutorService.shutdownNow();
		this.meshManagerExecutorService.shutdown();
		this.meshWorkerExecutorService.shutdownNow();
		this.paintQueue.shutdownNow();
		this.propagationQueue.shutdownNow();
		this.orthogonalViews().topLeft().viewer().stop();
		this.orthogonalViews().topRight().viewer().stop();
		this.orthogonalViews().bottomLeft().viewer().stop();
		LOG.debug("Sent stop requests everywhere");
	}

	/**
	 * Determine a good number of fetcher threads.
	 * @return half of all available processor, but no more than eight and no less than 1.
	 */
	public static int reasonableNumFetcherThreads()
	{
		return Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
	}

	/**
	 *
	 * @return global cache for caching basically everything (image data, meshes)
	 */
	public GlobalCache getGlobalCache()
	{
		return this.globalCache;
	}

	/**
	 *
	 * @return {@link ExecutorService} for managing mesh generation tasks
	 *
	 * TODO this should probably be removed by a management thread for every single {@link org.janelia.saalfeldlab.paintera.meshes.MeshManager}
	 * TODO like the {@link bdv.fx.viewer.render.PainterThread} for rendering
	 */
	public ExecutorService getMeshManagerExecutorService()
	{
		return this.meshManagerExecutorService;
	}

	/**
	 *
	 * @return {@link ExecutorService} for the heavy workload in mesh generation tasks
	 */
	public ExecutorService getMeshWorkerExecutorService()
	{
		return this.meshWorkerExecutorService;
	}

	/**
	 * create a {@link PainteraBaseView instance with reasonable default valuesand UI elements}
	 * delegates to {@link PainteraBaseView#defaultView()} with values set to
	 * <table summary="Arguments passed to delegated method call">
	 *     <tr>
	 *         <td>projectDir</td><td>{@code Files.createTempDirectory("paintera-base-view-").toString()}</td>
	 *         <td>crosshairConfig</td><td>default constructed {@link CrosshairConfig}</td>
	 *         <td>orthoSliceConfigBase</td><td>default constructed {@link OrthoSliceConfigBase}</td>
	 *         <td>navigationConfig</td><td>default constructed {@link NavigationConfig}</td>
	 *         <td>viewer3DConfig</td><td>default constructed {@link Viewer3DConfig}</td>
	 *         <td>screenScales</td><td>{@code [1.0, 0.5, 0.25]}</td>
	 *     </tr>
	 * </table>
	 * @return {@link PainteraBaseView#defaultView(String, CrosshairConfig, OrthoSliceConfigBase, NavigationConfig, Viewer3DConfig, double...) PainteraBaseView.defaultView}
	 * @throws IOException if thrown by one of the delegates
	 */
	public static DefaultPainteraBaseView defaultView() throws IOException
	{
		return defaultView(
				Files.createTempDirectory("paintera-base-view-").toString(),
				new CrosshairConfig(),
				new OrthoSliceConfigBase(),
				new NavigationConfig(),
				new Viewer3DConfig(),
				1.0, 0.5, 0.25);
	}

	/**
	 * create a {@link PainteraBaseView instance with reasonable default valuesand UI elements}
	 * @param projectDir project directory
	 * @param crosshairConfig settings for {@link org.janelia.saalfeldlab.paintera.ui.Crosshair cross hairs}
	 * @param orthoSliceConfigBase settings for {@link org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX}
	 * @param navigationConfig settings for {@link org.janelia.saalfeldlab.paintera.control.Navigation}
	 * @param viewer3DConfig settings for {@link Viewer3DFX}
	 * @param screenScales screen scales
	 * @return {@link DefaultPainteraBaseView}
	 */
	public static DefaultPainteraBaseView defaultView(
			final String projectDir,
			final CrosshairConfig crosshairConfig,
			final OrthoSliceConfigBase orthoSliceConfigBase,
			final NavigationConfig navigationConfig,
			final Viewer3DConfig viewer3DConfig,
			final double... screenScales)
	{
		final PainteraBaseView baseView = new PainteraBaseView(
				reasonableNumFetcherThreads(),
				ViewerOptions.options().screenScales(screenScales)
		);

		final KeyTracker   keyTracker   = new KeyTracker();
		final MouseTracker mouseTracker = new MouseTracker();

		final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
				baseView,
				() -> projectDir
		);

		final GridConstraintsManager gridConstraintsManager = new GridConstraintsManager();
		baseView.orthogonalViews().grid().manage(gridConstraintsManager);

		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				projectDir,
				gridConstraintsManager);

		final DefaultPainteraBaseView dpbv = new DefaultPainteraBaseView(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				gridConstraintsManager,
				defaultHandlers);

		final NavigationConfigNode navigationConfigNode = paneWithStatus.navigationConfigNode();
		paneWithStatus.getPane().addEventHandler(Event.ANY, defaultHandlers.getSourceSpecificGlobalEventHandler());
		paneWithStatus.getPane().addEventFilter(Event.ANY, defaultHandlers.getSourceSpecificGlobalEventFilter());

		final CoordinateConfigNode coordinateConfigNode = navigationConfigNode.coordinateConfigNode();
		coordinateConfigNode.listen(baseView.manager());

		paneWithStatus.crosshairConfigNode().bind(crosshairConfig);
		crosshairConfig.bindCrosshairsToConfig(paneWithStatus.crosshairs().values());

		final OrthoSliceConfig orthoSliceConfig = new OrthoSliceConfig(
				orthoSliceConfigBase,
				baseView.orthogonalViews().topLeft().viewer().visibleProperty(),
				baseView.orthogonalViews().topRight().viewer().visibleProperty(),
				baseView.orthogonalViews().bottomLeft().viewer().visibleProperty(),
				baseView.sourceInfo().hasSources()
		);
		paneWithStatus.orthoSliceConfigNode().bind(orthoSliceConfig);
		orthoSliceConfig.bindOrthoSlicesToConifg(
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().topLeft()),
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().topRight()),
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().bottomLeft())
		                                        );

		paneWithStatus.navigationConfigNode().bind(navigationConfig);
		navigationConfig.bindNavigationToConfig(defaultHandlers.navigation());

		paneWithStatus.viewer3DConfigNode().bind(viewer3DConfig);
		viewer3DConfig.bindViewerToConfig(baseView.viewer3D());

		return dpbv;
	}

	/**
	 * Utility class to hold objects used to turn {@link PainteraBaseView} into functional UI
	 */
	public static class DefaultPainteraBaseView
	{
		public final PainteraBaseView baseView;

		public final KeyTracker keyTracker;

		public final MouseTracker mouseTracker;

		public final BorderPaneWithStatusBars paneWithStatus;

		public final GridConstraintsManager gridConstraintsManager;

		public final PainteraDefaultHandlers handlers;

		private DefaultPainteraBaseView(
				final PainteraBaseView baseView,
				final KeyTracker keyTracker,
				final MouseTracker mouseTracker,
				final BorderPaneWithStatusBars paneWithStatus,
				final GridConstraintsManager gridConstraintsManager,
				final PainteraDefaultHandlers handlers)
		{
			super();
			this.baseView = baseView;
			this.keyTracker = keyTracker;
			this.mouseTracker = mouseTracker;
			this.paneWithStatus = paneWithStatus;
			this.gridConstraintsManager = gridConstraintsManager;
			this.handlers = handlers;
		}
	}

	/**
	 *
	 * @return current memory consumption of {{@link #getGlobalBackingCache()}}
	 */
	public long getCurrentMemoryUsageInBytes()
	{
		return ((MemoryBoundedSoftRefLoaderCache)this.globalBackingCache).getCurrentMemoryUsageInBytes();
	}

	/**
	 *
	 * @return the {@link LoaderCache} that backs {@link #getGlobalCache()}
	 */
	public LoaderCache<GlobalCache.Key<?>, ?> getGlobalBackingCache()
	{
		return this.globalBackingCache;
	}

}
