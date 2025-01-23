package org.janelia.saalfeldlab.paintera.ui;

import com.pivovarit.function.ThrowingConsumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.labels.blocks.LabelBlockLookup;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.paintera.Constants;
import org.janelia.saalfeldlab.paintera.LockFile;
import org.janelia.saalfeldlab.paintera.ProjectDirectory;
import org.janelia.saalfeldlab.paintera.Version;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.id.IdService;
import org.janelia.saalfeldlab.paintera.id.N5IdService;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

public class PainteraAlerts {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * delegates to {@link #alert(Alert.AlertType, boolean)} with {@code isResizable = true}.
	 *
	 * @param type type of alert
	 * @return {@link Alert} with the title set to {@link Constants#NAME}
	 */
	public static Alert alert(final Alert.AlertType type) {

		return alert(type, true);
	}

	/**
	 * @param type        type of alert
	 * @param isResizable set to {@code true} if dialog should be resizable
	 * @return {@link Alert} with the title set to {@link Constants#NAME}
	 */
	public static Alert alert(final Alert.AlertType type, boolean isResizable) {

		final AtomicReference<Alert> alertRef = new AtomicReference<>();
		try {
			InvokeOnJavaFXApplicationThread.invokeAndWait(() -> alertRef.set(new Alert(type)));
		} catch (InterruptedException e) {
			LOG.error("Could not create alert", e);
		}
		final Alert alert = alertRef.get();
		alert.setTitle(Constants.NAME);
		alert.setResizable(isResizable);

		/* Keep the alert on top */
		initAppDialog(alert);
		return alert;
	}

	/**
	 * Default app Owner and Modality as described by {@link #initAppDialog(Dialog, Window, Modality)}
	 *
	 * @param dialog top initOwner and initModality for
	 */
	public static void initAppDialog(Dialog<?> dialog) {

		initAppDialog(dialog, null, Modality.APPLICATION_MODAL);
	}



	/**
	 * initOwner as specified by {@link #initOwnerWithDefault(Dialog, Window)}.
	 * <br>
	 * initModality with {@link Modality}.
	 *
	 * @param dialog to init owner and modality for
	 * @param modality to initModality with
	 */
	public static void initAppDialog(Dialog<?> dialog, @Nonnull Modality modality) {

		initOwner(dialog);
		dialog.initModality(modality);
	}

	/**
	 * initOwner as specified by {@link #initOwnerWithDefault(Dialog, Window)}.
	 * <br>
	 * initModality with {@link Modality#APPLICATION_MODAL}.
	 *
	 * @param dialog to init owner and modality for
	 * @param owner  to init owner and modality with
	 * @param modality to initModality with
	 */
	public static void initAppDialog(Dialog<?> dialog, @Nullable Window owner, Modality modality) {

		initOwnerWithDefault(dialog, owner);
		dialog.initModality(modality);
	}

	/**
	 * try to grab a default Owner from the list of available windows
	 *
	 * @param dialog to initOwner for
	 */
	public static void initOwner(Dialog<?> dialog) {
		initOwnerWithDefault(dialog, null);
	}

	/**
	 * If owner is provided, use it. Otherwise, fallback vai {@link #initOwner(Dialog)}
	 *
	 * @param dialog to init owner for
	 * @param owner to initOwner with
	 */
	public static void initOwnerWithDefault(Dialog<?> dialog, @Nullable Window owner) {

		final Window window;
		if (owner == null && !Window.getWindows().isEmpty())
			window = Window.getWindows().getFirst();
		else
			window = owner;
		Optional.ofNullable(window).ifPresent(dialog::initOwner);
	}

	public static Alert confirmationWithMnemonics(final boolean isResizable) {

		return confirmation("_OK", "_Cancel", isResizable);
	}

	public static Alert confirmation(
			final String okButtonText,
			final String cancelButtonText,
			final boolean isResizable,
			final Window window
	) {

		final var alert = confirmation(okButtonText, cancelButtonText, isResizable);
		initAppDialog(alert, window, Modality.APPLICATION_MODAL);
		return alert;
	}

	public static Alert confirmation(
			final String okButtonText,
			final String cancelButtonText,
			final boolean isResizable) {

		final Alert alert = alert(Alert.AlertType.CONFIRMATION, isResizable);
		if (okButtonText != null)
			((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).setText(okButtonText);
		if (cancelButtonText != null)
			((Button)alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setText(cancelButtonText);
		return alert;
	}

	public static Alert informationWithMnemonics(final boolean isResizable) {

		return information("_OK", isResizable);
	}

	public static Alert information(
			final String okButtonText,
			final boolean isResizable) {

		final Alert alert = alert(Alert.AlertType.INFORMATION, isResizable);
		if (okButtonText != null)
			((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).setText(okButtonText);
		return alert;
	}

	/**
	 * Get a {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 *
	 * @param source used to determine block sizes for marching cubes
	 * @return {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 */
	public static LabelBlockLookup getLabelBlockLookupFromN5DataSource(
			final N5Reader reader,
			final String group,
			final DataSource<?, ?> source) {

		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("Define label-to-block-lookup for on-the-fly mesh generation");
		final TextArea ta = new TextArea(String.format("Could not deserialize label-to-block-lookup for dataset `%s' in N5 container `%s' " +
				"that is required for on the fly mesh generation. " +
				"If you are not interested in 3D meshes, press cancel. Otherwise, press OK. Generating meshes on the fly will be slow " +
				"as the sparsity of objects can not be utilized.", group, reader));
		ta.setEditable(false);
		ta.setWrapText(true);
		alert.getDialogPane().setContent(ta);
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final CellGrid[] grids = source.getGrids();
			long[][] dims = new long[grids.length][];
			int[][] blockSizes = new int[grids.length][];
			for (int i = 0; i < grids.length; ++i) {
				dims[i] = grids[i].getImgDimensions();
				blockSizes[i] = new int[grids[i].numDimensions()];
				grids[i].cellDimensions(blockSizes[i]);
			}
			LOG.debug("Returning block lookup returning all blocks.");
			return new LabelBlockLookupAllBlocks(dims, blockSizes);
		} else {
			return new LabelBlockLookupNoBlocks();
		}
	}

	/**
	 * Get a {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 *
	 * @param source used to determine block sizes for marching cubes
	 * @return {@link LabelBlockLookup} that returns all contained blocks ("OK") or no blocks ("CANCEL")
	 */
	public static LabelBlockLookup getLabelBlockLookupFromDataSource(final DataSource<?, ?> source) {

		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		alert.setHeaderText("Define label-to-block-lookup for on-the-fly mesh generation");
		final TextArea ta = new TextArea("Could not deserialize label-to-block-lookup that is required for on the fly mesh generation. " +
				"If you are not interested in 3D meshes, press cancel. Otherwise, press OK. Generating meshes on the fly will be slow " +
				"as the sparsity of objects can not be utilized.");
		ta.setEditable(false);
		ta.setWrapText(true);
		alert.getDialogPane().setContent(ta);
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final CellGrid[] grids = source.getGrids();
			long[][] dims = new long[grids.length][];
			int[][] blockSizes = new int[grids.length][];
			for (int i = 0; i < grids.length; ++i) {
				dims[i] = grids[i].getImgDimensions();
				blockSizes[i] = new int[grids[i].numDimensions()];
				grids[i].cellDimensions(blockSizes[i]);
			}
			LOG.debug("Returning block lookup returning all blocks.");
			return new LabelBlockLookupAllBlocks(dims, blockSizes);
		} else {
			return new LabelBlockLookupNoBlocks();
		}
	}

	public static IdService getN5IdServiceFromData(
			final N5Writer n5,
			final String dataset,
			final DataSource<? extends IntegerType<?>, ?> source) throws IOException {

		// sometimes NullPointerExceptions appear. This bug report may be relevant:
		// https://bugs.openjdk.java.net/browse/JDK-8157399

		final Alert alert = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION);
		final Button okButton = (Button)alert.getDialogPane().lookupButton(ButtonType.OK);
		final Button cancelButton = (Button)alert.getDialogPane().lookupButton(ButtonType.CANCEL);
		okButton.setText("_Ok");
		cancelButton.setText("_Cancel");
		alert.setHeaderText("maxId not specified in dataset.");
		final TextArea ta = new TextArea(String.format("Could not read maxId attribute from dataset `%s' in container `%s'. " +
				"You can specify the max id manually, or read it from the data set (this can take a long time if your data is big).\n" +
				"Alternatively, press cancel to load the data set without an id service. " +
				"Fragment-segment-assignments and selecting new (wrt to the data) labels require an id service " +
				"and will not be available if you press cancel.", dataset, n5));
		ta.setEditable(false);
		ta.setWrapText(true);
		final NumberField<LongProperty> maxIdField = NumberField.longField(
				org.janelia.saalfeldlab.labels.Label.getINVALID(),
				v -> true,
				ObjectField.SubmitOn.ENTER_PRESSED,
				ObjectField.SubmitOn.FOCUS_LOST);
		final BooleanBinding isValidMaxId = maxIdField.valueProperty().greaterThanOrEqualTo(0L);
		final ObjectProperty<ThreadWithCancellation> task = new SimpleObjectProperty<>();
		task.addListener((obs, oldv, newv) -> {
			if (oldv != null) {
				try {
					oldv.cancelAndJoin();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			if (newv != null)
				newv.start();
		});
		final BooleanBinding isRunning = task.isNotNull();
		final BooleanBinding isNotRunning = isRunning.not();
		final BooleanBinding cannotClickOk = isRunning.or(isValidMaxId.not());
		maxIdField.getTextField().editableProperty().bind(isNotRunning);
		okButton.disableProperty().bind(cannotClickOk);
		final String[] buttonTexts = {"_Scan Data", "_Abort"};
		final IntegerProperty currentIndex = new SimpleIntegerProperty(0);
		final Button scanButton = new Button(buttonTexts[0]);
		scanButton.setTooltip(new Tooltip(""));
		final StringBinding buttonTextNoMnemonics = Bindings.createStringBinding(() -> scanButton.getText().replace("_", ""), scanButton.textProperty());
		scanButton.getTooltip().textProperty().bind(buttonTextNoMnemonics);
		scanButton.setPrefWidth(100.0);
		final LongProperty initialValue = new SimpleLongProperty(maxIdField.valueProperty().get());

		final StatusBar statusBar = new StatusBar();
		statusBar.setPrefWidth(220.0);
		statusBar.setGraphic(scanButton);
		statusBar.setText("");
		statusBar.setTooltip(new Tooltip(""));
		final DoubleBinding percentProgress = statusBar.progressProperty().multiply(100.0);
		final IntegerBinding percentProgressInt = Bindings.createIntegerBinding(() -> (int)Math.round(percentProgress.get()), percentProgress);
		final StringBinding progressString = Bindings.createStringBinding(() -> String.format("%d%%", percentProgressInt.get()), percentProgressInt);
		statusBar.getTooltip().textProperty().bind(progressString);

		final Runnable runOnScanData = () -> {
			initialValue.set(maxIdField.valueProperty().get());
			final ThreadWithCancellation t = new ThreadWithCancellation(wasCanceled -> {
				findMaxId(
						source,
						0,
						maxId -> InvokeOnJavaFXApplicationThread.invoke(() -> {
							final ThreadWithCancellation currentTask = task.get();
							if (currentTask != null && !currentTask.wasCancelled())
								maxIdField.valueProperty().set(Math.max(maxId, maxIdField.valueProperty().get()));
						}),
						wasCanceled,
						p -> InvokeOnJavaFXApplicationThread.invoke(() -> statusBar.setProgress(p)));
				if (!wasCanceled.get()) {
					currentIndex.set(0);
					InvokeOnJavaFXApplicationThread.invoke(() -> {
						scanButton.setText(buttonTexts[0]);
						task.set(null);
					});
				}
			});
			t.setDaemon(true);
			t.setName(String.format("find-max-id-for-%s-in-%s", n5, dataset));
			task.set(t);
		};

		final Runnable runOnAbort = () -> {
			task.set(null);
			LOG.info("Setting next id field {} to initial value {}", maxIdField.valueProperty(), initialValue);
			maxIdField.valueProperty().set(initialValue.get());
			statusBar.setProgress(0.0);
		};

		final Runnable[] actions = {runOnScanData, runOnAbort};

		scanButton.setOnAction(e -> {
			final Runnable action = actions[currentIndex.get()];
			currentIndex.set((currentIndex.get() + 1) % actions.length);
			scanButton.setText(buttonTexts[currentIndex.get()]);
			InvokeOnJavaFXApplicationThread.invoke(action);
		});

		final HBox maxIdBox = new HBox(new Label("Max Id:"), maxIdField.getTextField(), statusBar);
		maxIdBox.setAlignment(Pos.CENTER);
		HBox.setHgrow(maxIdField.getTextField(), Priority.ALWAYS);
		alert.getDialogPane().setContent(new VBox(ta, maxIdBox));
		final Optional<ButtonType> bt = alert.showAndWait();
		if (bt.isPresent() && ButtonType.OK.equals(bt.get())) {
			final long maxId = maxIdField.valueProperty().get() + 1;
			n5.setAttribute(dataset, "maxId", maxId);
			return new N5IdService(n5, dataset, maxId);
		} else {
			task.set(null);
			return new IdService.IdServiceNotProvided();
		}
	}

	public static boolean ignoreLockFileDialog(
			final ProjectDirectory projectDirectory,
			final File directory) {

		return ignoreLockFileDialog(projectDirectory, directory, "_Cancel", true);
	}

	public static boolean ignoreLockFileDialog(
			final ProjectDirectory projectDirectory,
			final File directory,
			final String cancelButtontext,
			final boolean logFailure) {

		final SimpleBooleanProperty useItProperty = new SimpleBooleanProperty(true);
		final Alert alert = PainteraAlerts.confirmation("_Ignore Lock", cancelButtontext, true);
		alert.setHeaderText("Paintera project locked");
		alert.setContentText(String.format("" +
				"Paintera project at `%s' is currently locked. " +
				"A project is locked if it is accessed by a currently running Paintera instance " +
				"or a Paintera instance did not terminate properly, in which case you " +
				"may ignore the lock. " +
				"Please make sure that no currently running Paintera instances " +
				"access the project directory to avoid inconsistent project files.", directory));
		try {
			projectDirectory.setDirectory(directory, it -> {
				useItProperty.set(alert.showAndWait().filter(ButtonType.OK::equals).isPresent());
				return useItProperty.get();
			});
		} catch (final LockFile.UnableToCreateLock | IOException e) {
			if (logFailure) {
				LOG.error("Unable to ignore lock file", e);
				Exceptions.exceptionAlert(Constants.NAME, "Unable to ignore lock file", e).show();
			}
			return false;
		}
		return useItProperty.get();
	}

	public static Alert versionDialog() {

		final TextField versionField = new TextField(Version.VERSION_STRING);
		versionField.setEditable(false);
		versionField.setTooltip(new Tooltip(versionField.getText()));
		final HBox versionBox = new HBox(new Label("Paintera Version"), versionField);
		HBox.setHgrow(versionField, Priority.ALWAYS);
		versionBox.setAlignment(Pos.CENTER);
		final Alert alert = PainteraAlerts.alert(Alert.AlertType.INFORMATION, false);
		alert.getDialogPane().setContent(versionBox);
		alert.setHeaderText("Paintera Version");
		return alert;
	}

	@Deprecated
	public static boolean askConvertDeprecatedStatesShowAndWait(
			final BooleanProperty choiceProperty,
			final BooleanProperty rememberChoiceProperty,
			final Class<?> deprecatedStateType,
			final Class<?> convertedStateType,
			final Object datasetDescriptor) {

		if (rememberChoiceProperty.get())
			return choiceProperty.get();
		final Alert alert = askConvertDeprecatedStates(rememberChoiceProperty, deprecatedStateType, convertedStateType, datasetDescriptor);
		boolean choice = alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
		choiceProperty.setValue(choice);
		return choice;
	}

	@Deprecated
	public static Alert askConvertDeprecatedStates(
			final BooleanProperty rememberChoiceProperty,
			final Class<?> deprecatedStateType,
			final Class<?> convertedStateType,
			final Object datasetDescriptor) {

		final Alert alert = PainteraAlerts.confirmation("_Update", "_Skip", true);
		final TextArea message = new TextArea(String.format("" +
						"Dataset `%s' was opened in a deprecated format (%s). " +
						"Paintera can try to convert and update the dataset into a new format (%s) that supports relative data locations. " +
						"The new format is incompatible with Paintera versions 0.21.0 and older but updating datasets is recommended. " +
						"Backup files are generated for the Paintera files as well as for any dataset attributes that may have been modified " +
						"during the process.",
				datasetDescriptor,
				deprecatedStateType.getSimpleName(),
				convertedStateType.getSimpleName()));
		message.setWrapText(true);
		final CheckBox rememberChoice = new CheckBox("_Remember choice for all datasets in project");
		rememberChoice.selectedProperty().bindBidirectional(rememberChoiceProperty);
		alert.setHeaderText("Update deprecated data set");
		alert.getDialogPane().setContent(new VBox(message, rememberChoice));
		return alert;
	}

	private static void findMaxId(
			final DataSource<? extends IntegerType<?>, ?> source,
			final int level,
			final LongConsumer maxIdTracker,
			final AtomicBoolean cancel,
			final DoubleConsumer progressTracker) {

		final RandomAccessibleInterval<? extends IntegerType<?>> rai = source.getDataSource(0, level);
		final ReadOnlyLongWrapper totalNumVoxels = new ReadOnlyLongWrapper(Intervals.numElements(rai));
		maxIdTracker.accept(org.janelia.saalfeldlab.labels.Label.getINVALID());
		final LongProperty numProcessedVoxels = new SimpleLongProperty(0);
		numProcessedVoxels.addListener((obs, oldv, newv) -> progressTracker.accept(numProcessedVoxels.doubleValue() / totalNumVoxels.doubleValue()));

		final int[] blockSize = blockSizeFromRai(rai);
		final List<Interval> intervals = Grids.collectAllContainedIntervals(Intervals.minAsLongArray(rai), Intervals.maxAsLongArray(rai), blockSize);

		final ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		final List<Future<?>> futures = new ArrayList<>();
		for (final Interval interval : intervals) {
			futures.add(es.submit(() -> {
				synchronized (cancel) {
					if (cancel.get())
						return;
				}
				final RandomAccessibleInterval<? extends IntegerType<?>> slice = Views.interval(rai, interval);
				final long maxId = findMaxId(slice);
				synchronized (cancel) {
					if (!cancel.get()) {
						numProcessedVoxels.set(numProcessedVoxels.get() + Intervals.numElements(slice));
						maxIdTracker.accept(maxId);
					}
				}
			}));
		}
		futures.forEach(ThrowingConsumer.unchecked(Future::get));
		es.shutdown();
	}

	private static long findMaxId(final RandomAccessibleInterval<? extends IntegerType<?>> rai) {

		long maxId = org.janelia.saalfeldlab.labels.Label.getINVALID();
		for (final IntegerType<?> t : rai) {
			final long id = t.getIntegerLong();
			if (id > maxId)
				maxId = id;
		}
		return maxId;
	}

	private static int[] blockSizeFromRai(final RandomAccessibleInterval<?> rai) {

		if (rai instanceof AbstractCellImg<?, ?, ?, ?>) {
			final CellGrid cellGrid = ((AbstractCellImg<?, ?, ?, ?>)rai).getCellGrid();
			final int[] blockSize = new int[cellGrid.numDimensions()];
			cellGrid.cellDimensions(blockSize);
			LOG.debug("{} is a cell img with block size {}", rai, blockSize);
			return blockSize;
		}
		int argMaxDim = argMaxDim(rai);
		final int[] blockSize = Intervals.dimensionsAsIntArray(rai);
		blockSize[argMaxDim] = 1;
		return blockSize;
	}

	private static int argMaxDim(final Dimensions dims) {

		long max = -1;
		int argMax = -1;
		for (int d = 0; d < dims.numDimensions(); ++d) {
			if (dims.dimension(d) > max) {
				max = dims.dimension(d);
				argMax = d;
			}
		}
		return argMax;
	}

	private static final class ThreadWithCancellation extends Thread {

		private AtomicBoolean cancelled = new AtomicBoolean(false);

		private final Consumer<AtomicBoolean> task;

		public ThreadWithCancellation(final Consumer<AtomicBoolean> task) {

			this.task = task;
		}

		@Override
		public void run() {

			if (!cancelled.get())
				this.task.accept(this.cancelled);
		}

		public void cancel() {

			this.cancelled.set(true);
		}

		public void cancelAndJoin() throws InterruptedException {

			cancel();
			join();
		}

		public boolean wasCancelled() {

			return this.cancelled.get();
		}
	}
}
