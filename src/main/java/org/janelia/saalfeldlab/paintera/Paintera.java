package org.janelia.saalfeldlab.paintera;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import bdv.viewer.ViewerOptions;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.SaveProject.ProjectUndefined;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode;
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.control.CommitChanges;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.CannotPersist;
import org.janelia.saalfeldlab.paintera.data.mask.Masks;
import org.janelia.saalfeldlab.paintera.data.n5.CommitCanvasN5;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.HighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.stream.ModalGoldenAngleSaturatedHighlightingARGBStream;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.MakeUnchecked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class Paintera extends Application
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String PAINTERA_KEY = "paintera";

	public static final String NAME = "Paintera";

	public enum Error
	{
		NO_PROJECT_SPECIFIED(1, "No Paintera project specified");;
		public final int code;

		public final String description;

		private Error(final int code, final String description)
		{
			this.code = code;
			this.description = description;
		}
	}

	@Override
	public void start(final Stage stage) throws Exception
	{
		try
		{
			try
			{
				startImpl(stage);
			} catch (final RuntimeException re)
			{
				final Throwable cause = re.getCause();
				if (cause != null && cause instanceof Exception)
				{
					throw (Exception) cause;
				}
				else
				{
					throw re;
				}
			}
		} catch (final ProjectDirectoryNotSpecified e)
		{
			LOG.error(Error.NO_PROJECT_SPECIFIED.description);
			System.exit(Error.NO_PROJECT_SPECIFIED.code);
		} catch (final LockFile.UnableToCreateLock e)
		{
			LockFileAlreadyExistsDialog.showDialog(e);
		}
	}

	public void startImpl(final Stage stage) throws Exception
	{

		final Parameters              parameters         = getParameters();
		final String[]                args               = parameters.getRaw().stream().toArray(String[]::new);
		final PainteraCommandLineArgs painteraArgs       = new PainteraCommandLineArgs();
		final boolean                 parsedSuccessfully = Optional.ofNullable(CommandLine.call(
				painteraArgs,
				System.err,
				args
		                                                                                       )).orElse(false);
		Platform.setImplicitExit(true);

		// TODO introduce and throw appropriate exception instead of call to
		// Platform.exit
		if (!parsedSuccessfully)
		{
			Platform.exit();
			return;
		}

		final double[] screenScales = painteraArgs.screenScales();
		LOG.debug("Using screen scales {}", screenScales);

		final String projectDir = Optional
				.ofNullable(painteraArgs.project())
				.orElseGet(MakeUnchecked.supplier(() -> new ProjectDirectoryNotSpecifiedDialog(painteraArgs
						.defaultToTempDirectory())
						.showDialog(
								"No project directory specified on command line. You can specify a project directory " +
										"or start Paintera without specifying a project directory.").get()));

		final LockFile lockFile = new LockFile(new File(projectDir, ".paintera"), "lock");
		lockFile.lock();
		stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> lockFile.remove());

		final PainteraBaseView baseView = new PainteraBaseView(
				PainteraBaseView.reasonableNumFetcherThreads(),
				ViewerOptions.options().screenScales(screenScales),
				si -> s -> si.getState(s).interpolationProperty().get()
		);

		final OrthogonalViews<Viewer3DFX> orthoViews = baseView.orthogonalViews();

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

		final NavigationConfigNode navigationConfigNode = paneWithStatus.navigationConfigNode();

		final CoordinateConfigNode coordinateConfigNode = navigationConfigNode.coordinateConfigNode();
		coordinateConfigNode.listen(baseView.manager());

		// populate everything

		final Optional<JsonObject> loadedProperties = loadPropertiesIfPresent(projectDir);

		// TODO this can probably be hidden in
		// Properties.fromSerializedProperties
		final Map<Integer, SourceState<?, ?>> indexToState = new HashMap<>();

		final Properties properties = loadedProperties
				.map(lp -> Properties.fromSerializedProperties(
						lp,
						baseView,
						true,
						() -> projectDir,
						indexToState,
						gridConstraintsManager
				                                              ))
				.orElse(new Properties(baseView, gridConstraintsManager));

		paneWithStatus.crosshairConfigNode().bind(properties.crosshairConfig);
		properties.crosshairConfig.bindCrosshairsToConfig(paneWithStatus.crosshairs().values());

		final OrthoSliceConfig orthoSliceConfig = new OrthoSliceConfig(
				properties.orthoSliceConfig,
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

		paneWithStatus.navigationConfigNode().bind(properties.navigationConfig);
		properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation());

		paneWithStatus.viewer3DConfigNode().bind(properties.viewer3DConfig);
		properties.viewer3DConfig.bindViewerToConfig(baseView.viewer3D());

		//		gridConstraintsManager.set( properties.gridConstraints );

		paneWithStatus.saveProjectButtonOnActionProperty().set(event -> {
			try
			{
				SaveProject.persistProperties(
						projectDir,
						properties,
						GsonHelpers.builderWithAllRequiredSerializers(baseView, () -> projectDir).setPrettyPrinting()
				                             );
			} catch (final IOException e)
			{
				LOG.error("Unable to save project", e);
			} catch (final ProjectUndefined e)
			{
				LOG.error("Project undefined");
			}
		});

		// TODO this should probably happen in the properties.populate:
		properties.sourceInfo
				.trackSources()
				.stream()
				.map(properties.sourceInfo::getState)
				.filter(state -> state instanceof LabelSourceState<?, ?>)
				.map(state -> (LabelSourceState<?, ?>) state)
				.forEach(state -> {
					final long[] selIds = state.selectedIds().getActiveIds();
					final long   lastId = state.selectedIds().getLastSelection();
					state.selectedIds().deactivateAll();
					state.selectedIds().activate(selIds);
					state.selectedIds().activateAlso(lastId);
				});
		properties.clean();

		LOG.debug("Adding {} raw sources: {}", painteraArgs.rawSources().length, painteraArgs.rawSources());
		for (final String source : painteraArgs.rawSources())
		{
			addRawFromString(baseView, source);
		}

		LOG.debug("Adding {} label sources: {}", painteraArgs.labelSources().length, painteraArgs.labelSources());
		for (final String source : painteraArgs.labelSources())
		{
			addLabelFromString(baseView, source, projectDir);
		}

		properties.windowProperties.widthProperty.set(painteraArgs.width(properties.windowProperties.widthProperty.get
				()));
		properties.windowProperties.heightProperty.set(painteraArgs.height(properties.windowProperties.heightProperty
				.get()));

		properties.clean();

		final Scene scene = new Scene(paneWithStatus.getPane());
		if (LOG.isDebugEnabled())
		{
			scene.focusOwnerProperty().addListener((obs, oldv, newv) -> LOG.debug(
					"Focus changed: old={} new={}",
					oldv,
					newv
			                                                                     ));
		}

		setFocusTraversable(orthoViews, false);
		stage.setOnCloseRequest(new SaveOnExitDialog(baseView, properties, projectDir, baseView::stop));

		EventFX.KEY_PRESSED(
				"save project",
				e -> {
					e.consume();
					try
					{
						SaveProject.persistProperties(
								projectDir,
								properties,
								GsonHelpers.builderWithAllRequiredSerializers(
										baseView,
										() -> projectDir
								                                             ).setPrettyPrinting()
						                             );
					} catch (final IOException e1)
					{
						LOG.error("Unable to safe project", e1);
					} catch (final ProjectUndefined e1)
					{
						LOG.error("Project undefined");
					}
				},
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.S)
		                   ).installInto(paneWithStatus.getPane());

		EventFX.KEY_PRESSED("commit", e -> {
					LOG.debug("Showing commit dialog");
					e.consume();
					try
					{
						CommitChanges.commit(new CommitDialog(), baseView.sourceInfo().currentState().get());
					} catch (final CannotPersist e1)
					{
						LOG.error("Unable to persist canvas: {}", e1.getMessage());
					} catch (final UnableToPersist e1)
					{
						LOG.error("Unable to persist fragment-segment-assignment: {}", e1.getMessage());
					}
				},
				e -> keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.C)
		                   ).installInto(paneWithStatus.getPane());

		keyTracker.installInto(scene);
		scene.addEventFilter(MouseEvent.ANY, mouseTracker);
		stage.setScene(scene);
		stage.setWidth(properties.windowProperties.widthProperty.get());
		stage.setHeight(properties.windowProperties.heightProperty.get());
		properties.windowProperties.widthProperty.bind(stage.widthProperty());
		properties.windowProperties.heightProperty.bind(stage.heightProperty());
		properties.setGlobalTransformClean();

		stage.show();
	}

	public static void main(final String[] args)
	{
		launch(args);
	}

	private static void setFocusTraversable(
			final OrthogonalViews<?> view,
			final boolean isTraversable)
	{
		view.topLeft().viewer().setFocusTraversable(isTraversable);
		view.topRight().viewer().setFocusTraversable(isTraversable);
		view.bottomLeft().viewer().setFocusTraversable(isTraversable);
		view.grid().getBottomRight().setFocusTraversable(isTraversable);
	}

	public static Optional<? extends DataSource<?, ?>> addRawFromStringNoGenerics(
			final PainteraBaseView pbv,
			final String identifier,
			final Composite<ARGBType, ARGBType> composite) throws UnableToAddSource
	{
		return addRawFromString(pbv, identifier, composite);
	}

	private static <D extends NativeType<D> & RealType<D>, T extends Volatile<D> & NativeType<T> & RealType<T>>
	Optional<DataSource<D, T>> addRawFromString(
			final PainteraBaseView pbv,
			final String identifier) throws UnableToAddSource
	{
		return addRawFromString(pbv, identifier, new CompositeCopy<>());
	}

	private static <D extends NativeType<D> & RealType<D>, T extends Volatile<D> & NativeType<T> & RealType<T>>
	Optional<DataSource<D, T>> addRawFromString(
			final PainteraBaseView pbv,
			final String identifier,
			final Composite<ARGBType, ARGBType> composite) throws UnableToAddSource
	{
		if (!Pattern.matches("^[a-z]+://.+", identifier))
		{
			return addRawFromString(pbv, "file://" + identifier, composite);
		}

		if (Pattern.matches("^file://.+", identifier))
		{
			try
			{
				final String[] split   = identifier.replaceFirst("file://", "").split(":");
				final N5Reader reader  = N5Helpers.n5Reader(split[0], 64, 64, 64);
				final String   dataset = split[1];
				final String   name    = N5Helpers.lastSegmentOfDatasetPath(dataset);

				final DataSource<D, T> source = N5Helpers.openRawAsSource(
						reader,
						dataset,
						N5Helpers.getTransform(reader, dataset),
						pbv.getQueue(),
						0,
						name
				                                                         );

				final RawSourceState<D, T> state = new RawSourceState<>(
						source,
						new ARGBColorConverter.Imp1<>(),
						composite,
						name
				);

				pbv.addRawSource(state);
				return Optional.of(state.getDataSource());
			} catch (final Exception e)
			{
				throw e instanceof UnableToAddSource ? (UnableToAddSource) e : new UnableToAddSource(e);
			}
		}

		LOG.warn("Unable to generate raw source from {}", identifier);
		return Optional.empty();
	}

	public static void addLabelFromStringNoGenerics(
			final PainteraBaseView pbv,
			final String identifier,
			final String projectDirectory,
			final GetAssignment assignmentGenerator,
			GetN5IDService idServiceGenerator) throws UnableToAddSource
	{
		addLabelFromString(pbv, identifier, projectDirectory, assignmentGenerator, idServiceGenerator);
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void
	addLabelFromString(
			final PainteraBaseView pbv,
			final String identifier,
			final String projectDirectory) throws UnableToAddSource
	{
		addLabelFromString(pbv, identifier, projectDirectory, N5Helpers::assignments, N5Helpers::idService);
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void
	addLabelFromString(
			final PainteraBaseView pbv,
			final String identifier,
			final String projectDirectory,
			final GetAssignment assignmentGenerator,
			final GetN5IDService idServiceGenerator) throws UnableToAddSource
	{
		if (!Pattern.matches("^[a-z]+://.+", identifier))
		{
			addLabelFromString(
					pbv,
					"file://" + identifier,
					projectDirectory,
					assignmentGenerator,
					idServiceGenerator);
			return;
		}

		if (Pattern.matches("^file://.+", identifier))
		{
			try
			{
				final String[] split   = identifier.replaceFirst("file://", "").split(":");
				final N5Writer n5      = N5Helpers.n5Writer(split[0], 64, 64, 64);
				final String   dataset = split[1];
				LOG.warn("Adding label dataset={} dataset={}", split[0], dataset);
				final double[]                       resolution     = N5Helpers.getResolution(n5, dataset);
				final double[]                       offset         = N5Helpers.getOffset(n5, dataset);
				final AffineTransform3D              transform      = N5Helpers.fromResolutionAndOffset(
						resolution,
						offset
				                                                                                       );
				final Supplier<String>               nextCanvasDir  = Masks.canvasTmpDirDirectorySupplier(
						projectDirectory);
				final String                         name           = N5Helpers.lastSegmentOfDatasetPath(dataset);
				final SelectedIds                    selectedIds    = new SelectedIds();
				final IdService                      idService      = idServiceGenerator.getIdService(n5, dataset);
				final FragmentSegmentAssignmentState assignment     = assignmentGenerator.getAssignment(n5, dataset);
				final LockedSegmentsOnlyLocal        lockedSegments = new LockedSegmentsOnlyLocal(locked -> {
				});
				final ModalGoldenAngleSaturatedHighlightingARGBStream stream = new
						ModalGoldenAngleSaturatedHighlightingARGBStream(
						selectedIds,
						assignment,
						lockedSegments
				);
				final DataSource<D, T> dataSource = N5Helpers.openAsLabelSource(
						n5,
						dataset,
						transform,
						pbv.getQueue(),
						0,
						name
				                                                               );

				final DataSource<D, T> maskedSource = Masks.mask(
						dataSource,
						nextCanvasDir.get(),
						nextCanvasDir,
						new CommitCanvasN5(n5, dataset),
						pbv.getPropagationQueue()
				                                                );


				final LabelBlockLookup lookup = N5Helpers.getLabelBlockLookup(n5, dataset);
				InterruptibleFunction<Long, Interval[]>[] blockLoaders = IntStream
						.range(0, maskedSource.getNumMipmapLevels())
						.mapToObj(level -> InterruptibleFunction.fromFunction( MakeUnchecked.function( (MakeUnchecked.CheckedFunction<Long, Interval[]>) id -> lookup.read(level, id))))
						.toArray(InterruptibleFunction[]::new);

				final LabelSourceState<D, T> state = new LabelSourceState<>(
						maskedSource,
						HighlightingStreamConverter.forType(stream, dataSource.getType()),
						new ARGBCompositeAlphaYCbCr(),
						name,
						assignment,
						lockedSegments,
						idService,
						selectedIds,
						pbv.viewer3D().meshesGroup(),
						blockLoaders,
						pbv.getMeshManagerExecutorService(),
						pbv.getMeshWorkerExecutorService()
				);
				pbv.addLabelSource(state);
			} catch (final Exception e)
			{
				throw e instanceof UnableToAddSource ? (UnableToAddSource) e : new UnableToAddSource(e);
			}
		}
	}

	public static Optional<JsonObject> loadPropertiesIfPresent(final String root)
	{
		return loadPropertiesIfPresent(root, new GsonBuilder());
	}

	public static Optional<JsonObject> loadPropertiesIfPresent(final String root, final GsonBuilder builder)
	{
		try
		{
			final JsonObject properties = N5Helpers.n5Reader(root, builder, 64, 64, 64).getAttribute(
					"",
					"paintera",
					JsonObject.class
			                                                                                        );
			return Optional.of(properties);
		} catch (final IOException | NullPointerException e)
		{
			return Optional.empty();
		}
	}

	public static interface GetAssignment
	{

		public FragmentSegmentAssignmentState getAssignment(final N5Writer writer, final String dataset)
		throws IOException;

	}

	public static interface GetN5IDService
	{

		public IdService getIdService(final N5Writer writer, final String dataset) throws IOException;

	}

}
