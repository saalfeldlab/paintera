package org.janelia.saalfeldlab.paintera;

import bdv.viewer.ViewerOptions;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.janelia.saalfeldlab.fx.event.EventFX;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.fx.event.MouseTracker;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;
import org.janelia.saalfeldlab.paintera.SaveProject.ProjectUndefined;
import org.janelia.saalfeldlab.paintera.config.CoordinateConfigNode;
import org.janelia.saalfeldlab.paintera.config.NavigationConfigNode;
import org.janelia.saalfeldlab.paintera.config.OrthoSliceConfig;
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig;
import org.janelia.saalfeldlab.paintera.control.CommitChanges;
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType;
import org.janelia.saalfeldlab.paintera.control.assignment.UnableToPersist;
import org.janelia.saalfeldlab.paintera.data.mask.exception.CannotPersist;
import org.janelia.saalfeldlab.paintera.serialization.GsonHelpers;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.janelia.saalfeldlab.paintera.state.HasSelectedIds;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.janelia.saalfeldlab.paintera.viewer3d.Viewer3DFX;
import org.janelia.saalfeldlab.util.n5.N5Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import pl.touk.throwing.ThrowingFunction;
import pl.touk.throwing.ThrowingSupplier;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
		final boolean                 parsedSuccessfully = Optional
				.ofNullable(CommandLine.call(
					painteraArgs,
					System.err,
					args))
				.orElse(false);
		Platform.setImplicitExit(true);

		// TODO introduce and throw appropriate exception instead of call to
		// Platform.exit
		if (!parsedSuccessfully)
		{
			Platform.exit();
			return;
		}

		stage.setTitle("Paintera");
		stage.getIcons().addAll(
			new Image(getClass().getResourceAsStream("/icon-16.png")),
			new Image(getClass().getResourceAsStream("/icon-32.png")),
			new Image(getClass().getResourceAsStream("/icon-48.png")),
			new Image(getClass().getResourceAsStream("/icon-64.png")),
			new Image(getClass().getResourceAsStream("/icon-96.png")),
			new Image(getClass().getResourceAsStream("/icon-128.png")));

		final String projectDir = Optional
				.ofNullable(painteraArgs.project())
				.orElseGet(ThrowingSupplier.unchecked(() -> new ProjectDirectoryNotSpecifiedDialog(painteraArgs
						.defaultToTempDirectory())
						.showDialog(
								"No project directory specified on command line. You can specify a project directory " +
										"or start Paintera without specifying a project directory.").get()));

		final LockFile lockFile = new LockFile(new File(projectDir, ".paintera"), "lock");
		lockFile.lock();
		stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> lockFile.remove());

		final PainteraBaseView baseView = new PainteraBaseView(
				PainteraBaseView.reasonableNumFetcherThreads(),
				ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()));

		final OrthogonalViews<Viewer3DFX> orthoViews = baseView.orthogonalViews();

		final KeyTracker   keyTracker   = new KeyTracker();
		final MouseTracker mouseTracker = new MouseTracker();

		final BorderPaneWithStatusBars paneWithStatus = new BorderPaneWithStatusBars(
				baseView,
				() -> projectDir);

		final GridConstraintsManager gridConstraintsManager = new GridConstraintsManager();
		baseView.orthogonalViews().grid().manage(gridConstraintsManager);

		final PainteraDefaultHandlers defaultHandlers = new PainteraDefaultHandlers(
				baseView,
				keyTracker,
				mouseTracker,
				paneWithStatus,
				projectDir,
				gridConstraintsManager);

		final NavigationConfigNode navigationConfigNode = paneWithStatus.navigationConfigNode();

		final CoordinateConfigNode coordinateConfigNode = navigationConfigNode.coordinateConfigNode();
		coordinateConfigNode.listen(baseView.manager());

		paneWithStatus.scaleBarOverlayConfigNode().bindBidirectionalTo(defaultHandlers.scaleBarConfig());

		// populate everything

		final Optional<JsonObject> loadedProperties = loadPropertiesIfPresent(projectDir);

		// TODO this can probably be hidden in
		// Properties.fromSerializedProperties
		final Map<Integer, SourceState<?, ?>> indexToState = new HashMap<>();

		final Properties properties = loadedProperties
				.map(ThrowingFunction.unchecked(lp -> Properties.fromSerializedProperties(
						lp,
						baseView,
						true,
						() -> projectDir,
						indexToState,
						gridConstraintsManager
				                                              )))
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
		orthoSliceConfig.bindOrthoSlicesToConfig(
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().topLeft()),
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().topRight()),
				paneWithStatus.orthoSlices().get(baseView.orthogonalViews().bottomLeft())
		                                        );

		paneWithStatus.screenScalesConfigNode().bind(properties.screenScalesConfig);
		properties.screenScalesConfig.screenScalesProperty().addListener((obs, oldv, newv) -> baseView.orthogonalViews().setScreenScales(newv.getScalesCopy()));
		baseView.orthogonalViews().setScreenScales(properties.screenScalesConfig.screenScalesProperty().get().getScalesCopy());
		if (painteraArgs.wereScreenScalesProvided())
			properties.screenScalesConfig.screenScalesProperty().set(new ScreenScalesConfig.ScreenScales(painteraArgs.screenScales()));

		paneWithStatus.navigationConfigNode().bind(properties.navigationConfig);
		properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation());

		paneWithStatus.viewer3DConfigNode().bind(properties.viewer3DConfig);
		properties.viewer3DConfig.bindViewerToConfig(baseView.viewer3D());

		paneWithStatus.bookmarkConfigNode().bookmarkConfigProperty().set(defaultHandlers.bookmarkConfig());

		defaultHandlers.scaleBarConfig().bindBidirectionalTo(properties.scaleBarOverlayConfig);



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
				.filter(state -> state instanceof HasSelectedIds)
				.map(state -> (HasSelectedIds) state)
				.forEach(state -> {
					final long[] selIds = state.selectedIds().getActiveIds();
					final long   lastId = state.selectedIds().getLastSelection();
					state.selectedIds().deactivateAll();
					state.selectedIds().activate(selIds);
					state.selectedIds().activateAlso(lastId);
				});
		properties.clean();

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

					if (!baseView.allowedActionsProperty().get().isAllowed(MenuActionType.SaveProject))
					{
						final Alert cannotSaveProjectDialog = PainteraAlerts.alert(Alert.AlertType.WARNING);
						cannotSaveProjectDialog.setHeaderText("Cannot currently save the project.");
						cannotSaveProjectDialog.setContentText("Please return to the normal application mode.");
						cannotSaveProjectDialog.show();
						return;
					}

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
				e -> baseView.allowedActionsProperty().get().isAllowed(MenuActionType.CommitCanvas) && keyTracker.areOnlyTheseKeysDown(KeyCode.CONTROL, KeyCode.C)
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

	private static Optional<JsonObject> loadPropertiesIfPresent(final String root)
	{
		return loadPropertiesIfPresent(root, new GsonBuilder());
	}

	private static Optional<JsonObject> loadPropertiesIfPresent(final String root, final GsonBuilder builder)
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

}
