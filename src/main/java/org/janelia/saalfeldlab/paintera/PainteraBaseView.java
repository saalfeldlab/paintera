package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;

import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import gnu.trove.set.hash.TLongHashSet;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.Pane;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.composition.CompositeProjectorPreMultiply;
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode;
import org.janelia.saalfeldlab.paintera.config.CrosshairConfig;
import org.janelia.saalfeldlab.paintera.config.NavigationConfig;
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfigBase;
import org.janelia.saalfeldlab.paintera.config.Viewer3DConfig;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PainteraBaseView
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SourceInfo sourceInfo = new SourceInfo();

	private final GlobalTransformManager manager = new GlobalTransformManager();

	private final SharedQueue cacheControl;

	private final ViewerOptions viewerOptions;

	private final Viewer3DFX viewer3D = new Viewer3DFX(1, 1);

	private final OrthogonalViews<Viewer3DFX> views;

	private final ObservableList<SourceAndConverter<?>> visibleSourcesAndConverters = sourceInfo
			.trackVisibleSourcesAndConverters();

	private final ListChangeListener<SourceAndConverter<?>> vsacUpdate;

	private final ExecutorService generalPurposeExecutorService = Executors.newFixedThreadPool(
			3,
			new NamedThreadFactory("paintera-thread-%d")
	                                                                                          );

	private final ExecutorService meshManagerExecutorService = Executors.newFixedThreadPool(
			3,
			new NamedThreadFactory("paintera-mesh-manager-%d")
	                                                                                       );

	private final ExecutorService meshWorkerExecutorService = Executors.newFixedThreadPool(
			10,
			new NamedThreadFactory("paintera-mesh-worker-%d")
	                                                                                      );

	private final ExecutorService paintQueue = Executors.newFixedThreadPool(1);

	private final ExecutorService propagationQueue = Executors.newFixedThreadPool(1);

	public PainteraBaseView(final int numFetcherThreads, final Function<SourceInfo, Function<Source<?>,
			Interpolation>> interpolation)
	{
		this(numFetcherThreads, ViewerOptions.options(), interpolation);
	}

	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions)
	{
		this(numFetcherThreads, viewerOptions, si -> source -> si.getState(source).interpolationProperty().get());
	}

	public PainteraBaseView(
			final int numFetcherThreads,
			final ViewerOptions viewerOptions,
			final Function<SourceInfo, Function<Source<?>, Interpolation>> interpolation)
	{
		super();
		this.cacheControl = new SharedQueue(numFetcherThreads);
		this.viewerOptions = viewerOptions
				.accumulateProjectorFactory(new CompositeProjectorPreMultiply.CompositeProjectorFactory(sourceInfo
						.composites()))
				// .accumulateProjectorFactory( new
				// ClearingCompositeProjector.ClearingCompositeProjectorFactory<>(
				// sourceInfo.composites(), new ARGBType() ) )
				.numRenderingThreads(Math.min(3, Math.max(1, Runtime.getRuntime().availableProcessors() / 3)));
		this.views = new OrthogonalViews<>(
				manager,
				cacheControl,
				this.viewerOptions,
				viewer3D,
				interpolation.apply(sourceInfo)
		);
		this.vsacUpdate = change -> views.setAllSources(visibleSourcesAndConverters);
		visibleSourcesAndConverters.addListener(vsacUpdate);
		LOG.debug("Meshes group={}", viewer3D.meshesGroup());
	}

	public OrthogonalViews<Viewer3DFX> orthogonalViews()
	{
		return this.views;
	}

	public Viewer3DFX viewer3D()
	{
		return this.viewer3D;
	}

	public SourceInfo sourceInfo()
	{
		return this.sourceInfo;
	}

	public Pane pane()
	{
		return orthogonalViews().pane();
	}

	public GlobalTransformManager manager()
	{
		return this.manager;
	}

	@SuppressWarnings("unchecked")
	public <D, T> void addState(final SourceState<D, T> state)
	{
		if (state instanceof LabelSourceState<?, ?>)
		{
			addLabelSource((LabelSourceState) state);
		}
		else if (state instanceof RawSourceState<?, ?>)
		{
			addRawSource((RawSourceState) state);
		}
		else
		{
			addGenericState(state);
		}
	}

	public <D, T> void addGenericState(final SourceState<D, T> state)
	{
		sourceInfo.addState(state);
	}

	public <D extends RealType<D> & NativeType<D>, T extends AbstractVolatileNativeRealType<D, T>> RawSourceState<D,
			T> addSingleScaleRawSource(
			final RandomAccessibleInterval<D> data,
			double[] resolution,
			double[] offset,
			double min,
			double max,
			String name
	                                                                                                                                           )
	{
		RawSourceState<D, T> state = RawSourceState.simpleSourceFromSingleRAI(data, resolution, offset, min, max,
				name);
		InvokeOnJavaFXApplicationThread.invoke(() -> addRawSource(state));
		return state;
	}

	public <T extends RealType<T>, U extends RealType<U>> void addRawSource(
			final RawSourceState<T, U> state)
	{
		LOG.debug("Adding raw state={}", state);
		sourceInfo.addState(state);
		final ARGBColorConverter<U> conv      = state.converter();
		final ARGBColorConverter<U> colorConv = conv;
		colorConv.colorProperty().addListener((obs, oldv, newv) -> orthogonalViews().requestRepaint());
		colorConv.minProperty().addListener((obs, oldv, newv) -> orthogonalViews().requestRepaint());
		colorConv.maxProperty().addListener((obs, oldv, newv) -> orthogonalViews().requestRepaint());
		colorConv.alphaProperty().addListener((obs, oldv, newv) -> orthogonalViews().requestRepaint());
	}

	public <D extends IntegerType<D> & NativeType<D>, T extends Volatile<D> & IntegerType<T>> LabelSourceState<D, T>
	addSingleScaleLabelSource(
			final RandomAccessibleInterval<D> data,
			final double[] resolution,
			final double[] offset,
			final long maxId,
			final String name
	                                                                                                                                          )
	{
		LabelSourceState<D, T> state = LabelSourceState.simpleSourceFromSingleRAI(
				data,
				resolution,
				offset,
				maxId,
				name,
				viewer3D().meshesGroup(),
				meshManagerExecutorService,
				meshWorkerExecutorService
		                                                                         );
		InvokeOnJavaFXApplicationThread.invoke(() -> addLabelSource(state));
		return state;
	}

	public <D extends IntegerType<D>, T extends Type<T>> void addLabelSource(
			final LabelSourceState<D, T> state)
	{
		LOG.debug("Adding label state={}", state);
		final Converter<T, ARGBType> converter = state.converter();
		if (converter instanceof HighlightingStreamConverter<?>)
		{
			final AbstractHighlightingARGBStream stream = ((HighlightingStreamConverter<?>) converter).getStream();
			stream.addListener(obs -> orthogonalViews().requestRepaint());
		}

		orthogonalViews().applyToAll(vp -> state.assignment().addListener(obs -> vp.requestRepaint()));
		orthogonalViews().applyToAll(vp -> state.selectedIds().addListener(obs -> vp.requestRepaint()));
		orthogonalViews().applyToAll(vp -> state.lockedSegments().addListener(obs -> vp.requestRepaint()));

		state.meshManager().areMeshesEnabledProperty().bind(viewer3D.isMeshesEnabledProperty());

		sourceInfo.addState(state.getDataSource(), state);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <D> LongFunction<Converter<D, BoolType>> equalsMaskForType(final D d)
	{
		if (d instanceof LabelMultisetType) { return (LongFunction) equalMaskForLabelMultisetType(); }

		if (d instanceof IntegerType<?>) { return (LongFunction) equalMaskForIntegerType(); }

		if (d instanceof RealType<?>) { return (LongFunction) equalMaskForRealType(); }

		return null;
	}

	public static LongFunction<Converter<LabelMultisetType, BoolType>> equalMaskForLabelMultisetType()
	{
		return id -> (s, t) -> t.set(s.contains(id));
	}

	public static <D extends IntegerType<D>> LongFunction<Converter<D, BoolType>> equalMaskForIntegerType()
	{
		return id -> (s, t) -> t.set(s.getIntegerLong() == id);
	}

	public static <D extends RealType<D>> LongFunction<Converter<D, BoolType>> equalMaskForRealType()
	{
		return id -> (s, t) -> t.set(s.getRealDouble() == id);
	}

	public ExecutorService generalPurposeExecutorService()
	{
		return this.generalPurposeExecutorService;
	}

	public static double[][] scaleFactorsFromAffineTransforms(final Source<?> source)
	{
		final double[][]        scaleFactors = new double[source.getNumMipmapLevels()][3];
		final AffineTransform3D reference    = new AffineTransform3D();
		source.getSourceTransform(0, 0, reference);
		for (int level = 0; level < scaleFactors.length; ++level)
		{
			final double[]          factors   = scaleFactors[level];
			final AffineTransform3D transform = new AffineTransform3D();
			source.getSourceTransform(0, level, transform);
			factors[0] = transform.get(0, 0) / reference.get(0, 0);
			factors[1] = transform.get(1, 1) / reference.get(1, 1);
			factors[2] = transform.get(2, 2) / reference.get(2, 2);
		}

		{
			LOG.debug("Generated scaling factors:");
			Arrays.stream(scaleFactors).map(Arrays::toString).forEach(LOG::debug);
		}

		return scaleFactors;
	}

	@SuppressWarnings("unchecked")
	public static <T> BiConsumer<T, TLongHashSet> collectLabels(final T type)
	{
		if (type instanceof LabelMultisetType)
		{
			return (BiConsumer<T, TLongHashSet>) collectLabelsFromLabelMultisetType();
		}
		if (type instanceof IntegerType<?>) { return (BiConsumer<T, TLongHashSet>) collectLabelsFromIntegerType(); }
		if (type instanceof RealType<?>) { return (BiConsumer<T, TLongHashSet>) collectLabelsFromRealType(); }
		return null;
	}

	private static BiConsumer<LabelMultisetType, TLongHashSet> collectLabelsFromLabelMultisetType()
	{
		return (lbl, set) -> lbl.entrySet().forEach(entry -> set.add(entry.getElement().id()));
	}

	private static <I extends IntegerType<I>> BiConsumer<I, TLongHashSet> collectLabelsFromIntegerType()
	{
		return (lbl, set) -> set.add(lbl.getIntegerLong());
	}

	private static <R extends RealType<R>> BiConsumer<R, TLongHashSet> collectLabelsFromRealType()
	{
		return (lbl, set) -> set.add((long) lbl.getRealDouble());
	}

	public ExecutorService getPaintQueue()
	{
		return this.paintQueue;
	}

	public ExecutorService getPropagationQueue()
	{
		return this.propagationQueue;
	}

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
		this.cacheControl.shutdown();
		LOG.debug("Sent stop requests everywhere");
	}

	public static int reasonableNumFetcherThreads()
	{
		return Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
	}

	public SharedQueue getQueue()
	{
		return this.cacheControl;
	}

	public ExecutorService getMeshManagerExecutorService()
	{
		return this.meshManagerExecutorService;
	}

	public ExecutorService getMeshWorkerExecutorService()
	{
		return this.meshWorkerExecutorService;
	}

	public static DefaultPainteraBaseView defaultView() throws IOException
	{
		return defaultView(
				Files.createTempDirectory("paintera-base-view-").toString(),
				new CrosshairConfig(),
				new OrthoSliceConfigBase(),
				new NavigationConfig(),
				new Viewer3DConfig(),
				1.0, 0.5, 0.25
		                  );
	}

	public static DefaultPainteraBaseView defaultView(
			final String projectDir,
			final CrosshairConfig crosshairConfig,
			final OrthoSliceConfigBase orthoSliceConfigBase,
			final NavigationConfig navigationConfig,
			final Viewer3DConfig viewer3DConfig,
			final double... screenScales)
	{
		final PainteraBaseView baseView = new PainteraBaseView(
				Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)),
				ViewerOptions.options().screenScales(screenScales),
				si -> s -> si.getState(s).interpolationProperty().get()
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
				gridConstraintsManager
		);

		final DefaultPainteraBaseView dpbv = new DefaultPainteraBaseView(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				gridConstraintsManager,
				defaultHandlers
		);

		final NavigationConfigNode navigationConfigNode = paneWithStatus.navigationConfigNode();

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

}
