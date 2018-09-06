package org.janelia.saalfeldlab.paintera.ui.opendialog.menu.n5;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import org.janelia.saalfeldlab.fx.ui.Exceptions;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.janelia.saalfeldlab.paintera.ui.opendialog.BackendDialog;
import org.janelia.saalfeldlab.paintera.ui.opendialog.CombinesErrorMessages;
import org.janelia.saalfeldlab.paintera.ui.opendialog.NameField;
import org.janelia.saalfeldlab.paintera.ui.opendialog.OpenSourceDialog;
import org.janelia.saalfeldlab.paintera.ui.opendialog.menu.OpenDialogMenuEntry;
import org.janelia.saalfeldlab.paintera.ui.opendialog.meta.MetaPanel;
import org.janelia.saalfeldlab.util.HashWrapper;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class N5OpenSourceDialog extends Dialog<BackendDialog> implements CombinesErrorMessages {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "N5", priority = Double.MAX_VALUE)
	public static class N5FSOpener implements OpenDialogMenuEntry {
		private static final FileSystem fs = new FileSystem();

		@Override
		public BiConsumer<PainteraBaseView, String> onAction() {
			return (pbv, projectDirectory) -> {
				try {
					GenericBackendDialogN5 dialog = fs.backendDialog(pbv.getPropagationQueue());
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("N5");
					Optional<BackendDialog> backend = osDialog.showAndWait();
					if (backend == null || !backend.isPresent())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, pbv, projectDirectory);
				} catch (Exception e1) {
					Exceptions.exceptionAlert(Paintera.NAME, "Unable to open N5 data set", e1);
				}
			};
		}
	}

	@Plugin(type = OpenDialogMenuEntry.class, menuPath = "HDF5", priority = Double.MAX_VALUE / 2.0)
	public static class N5HDFOpener implements OpenDialogMenuEntry {

		private static final HDF5 hdf5 = new HDF5();

		@Override
		public BiConsumer<PainteraBaseView, String> onAction() {
			return (pbv, projectDirectory) -> {
				try {
					GenericBackendDialogN5 dialog = hdf5.backendDialog(pbv.getPropagationQueue());
					N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("HDF5");
					Optional<BackendDialog> backend = osDialog.showAndWait();
					if (backend == null || !backend.isPresent())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, pbv, projectDirectory);
				} catch (Exception e1) {
					Exceptions.exceptionAlert(Paintera.NAME, "Unable to open HDF5 data set", e1);
				}
			};
		}
	}

	@Plugin(type = OpenDialogMenuEntry.class, menuPath= "Google Cloud", priority = Double.MAX_VALUE / 4.0)
	public static class GoogleCloudOpener implements OpenDialogMenuEntry {

		@Override
		public BiConsumer<PainteraBaseView, String> onAction() {
			return (pbv, projectDirectory) -> {
				try {
					final GoogleCloud googleCloud = new GoogleCloud();
					final GenericBackendDialogN5 dialog = googleCloud.backendDialog(pbv.getPropagationQueue());
					final N5OpenSourceDialog osDialog = new N5OpenSourceDialog(pbv, dialog);
					osDialog.setHeaderFromBackendType("Google Cloud");
					Optional<BackendDialog> backend = osDialog.showAndWait();
					if (backend == null || !backend.isPresent())
						return;
					N5OpenSourceDialog.addSource(osDialog.getName(), osDialog.getType(), dialog, pbv, projectDirectory);
				} catch (Exception e1) {
					Exceptions.exceptionAlert(Paintera.NAME, "Unable to open Google Cloud data set", e1);
				}
			};
		}
	}

	private final VBox dialogContent;

	private final GridPane grid;

	private final ComboBox<OpenSourceDialog.TYPE> typeChoice;

	private final Label errorMessage;

	private final TitledPane errorInfo;

	private final ObservableList<OpenSourceDialog.TYPE> typeChoices = FXCollections.observableArrayList(OpenSourceDialog.TYPE.values());

	private final NameField nameField = new NameField(
			"Source name",
			"Specify source name (required)",
			new InnerShadow(10, Color.ORANGE)
	);

	private final BooleanBinding isError;

	private final ExecutorService propagationExecutor;

	private final BackendDialog backendDialog;

	private final MetaPanel metaPanel = new MetaPanel();

	private final Button revertAxisOrder = new Button(" Revert axis");

	private final HBox revertAxisHBox = new HBox(revertAxisOrder);

	public N5OpenSourceDialog(final PainteraBaseView viewer, final BackendDialog backendDialog) {
		super();

		this.backendDialog = backendDialog;

		this.propagationExecutor = viewer.getPropagationQueue();

		this.setTitle("Open data set");
		this.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		this.errorMessage = new Label("");
		this.errorInfo = new TitledPane("", errorMessage);
		this.isError = Bindings.createBooleanBinding(() -> Optional.ofNullable(this.errorMessage.textProperty().get())
				.orElse(
						"").length() > 0, this.errorMessage.textProperty());
		errorInfo.textProperty().bind(Bindings.createStringBinding(
				() -> this.isError.get() ? "ERROR" : "",
				this.isError
		));

		this.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(this.isError);
		this.errorInfo.visibleProperty().bind(this.isError);

		final Tooltip revertAxisTooltip = new Tooltip("If you data is using `zyx` you should revert it.");
		this.grid = new GridPane();
		this.nameField.errorMessageProperty().addListener((obs, oldv, newv) -> combineErrorMessages());
		this.revertAxisOrder.setTooltip(revertAxisTooltip);
		this.revertAxisHBox.setAlignment(Pos.BASELINE_RIGHT);
		this.dialogContent = new VBox(10, nameField.textField(), grid, metaPanel.getPane(), revertAxisHBox, errorInfo);
		this.setResizable(true);

		GridPane.setMargin(this.backendDialog.getDialogNode(), new Insets(0, 0, 0, 30));
		this.grid.add(this.backendDialog.getDialogNode(), 1, 0);
		GridPane.setHgrow(this.backendDialog.getDialogNode(), Priority.ALWAYS);

		this.getDialogPane().setContent(dialogContent);
		final VBox choices = new VBox();
		this.typeChoice = new ComboBox<>(typeChoices);
		this.metaPanel.bindDataTypeTo(this.typeChoice.valueProperty());

		final DoubleProperty[] res = backendDialog.resolution();
		final DoubleProperty[] off = backendDialog.offset();
		this.metaPanel.listenOnResolution(res[0], res[1], res[2]);
		this.metaPanel.listenOnOffset(off[0], off[1], off[2]);
		this.metaPanel.listenOnMinMax(backendDialog.min(), backendDialog.max());
		backendDialog.errorMessage().addListener((obs, oldErr, newErr) -> combineErrorMessages());
		backendDialog.nameProperty().addListener((obs, oldName, newName) -> Optional.ofNullable(newName).ifPresent(nameField.textField().textProperty()::set));
		combineErrorMessages();
		Optional.ofNullable(backendDialog.nameProperty().get()).ifPresent(nameField.textField()::setText);

		this.revertAxisOrder.setOnAction(event -> {
			backendDialog.setResolution(revert(metaPanel.getResolution()));
			backendDialog.setOffset(revert(metaPanel.getOffset()));
		});
		HBox.setHgrow(revertAxisHBox, Priority.ALWAYS);

		this.typeChoice.setValue(typeChoices.get(0));
		this.typeChoice.setMinWidth(100);
		choices.getChildren().addAll(this.typeChoice);
		this.grid.add(choices, 0, 0);
		this.setResultConverter(button -> button.equals(ButtonType.OK) ? backendDialog : null);
		combineErrorMessages();
		setTitle(Paintera.NAME);

	}

	public OpenSourceDialog.TYPE getType() {
		return typeChoice.getValue();
	}

	public String getName() {
		return nameField.getText();
	}

	public MetaPanel getMeta() {
		return this.metaPanel;
	}

	@Override
	public Collection<ObservableValue<String>> errorMessages() {
		return Arrays.asList(this.nameField.errorMessageProperty(), getBackend().errorMessage());
	}

	@Override
	public Consumer<Collection<String>> combiner() {
		return strings -> InvokeOnJavaFXApplicationThread.invoke(() -> this.errorMessage.setText(String.join(
				"\n",
				strings
		)));
	}

	public BackendDialog getBackend() {
		return this.backendDialog;
	}

	private static final double[] revert(final double[] array) {
		final double[] reverted = new double[array.length];
		for (int i = 0; i < array.length; ++i) {
			reverted[i] = array[array.length - 1 - i];
		}
		return reverted;
	}

	public static void addSource(
			final String name,
			final OpenSourceDialog.TYPE type,
			final BackendDialog dataset,
			final PainteraBaseView viewer,
			final String projectDirectory) throws Exception {
		LOG.debug("Type={}", type);
		switch (type) {
			case RAW:
				LOG.trace("Adding raw data");
				addRaw(name, dataset, viewer);
				break;
			case LABEL:
				addLabel(name, dataset, viewer, projectDirectory);
				break;
			default:
				break;
		}
	}

	private static <T extends RealType<T> & NativeType<T>, V extends AbstractVolatileRealType<T, V> & NativeType<V>> void
	addRaw(
			final String name,
			final BackendDialog dataset,
			PainteraBaseView viewer) throws Exception {
		final RawSourceState<T, V> raw = dataset.getRaw(name, viewer.getQueue(), viewer.getQueue().getNumPriorities() - 1);
		LOG.debug("Got raw: {}", raw);
		InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addRawSource(raw));
	}

	private static <D extends NativeType<D> & IntegerType<D>, T extends Volatile<D> & NativeType<T>> void addLabel(
			final String name,
			final BackendDialog dataset,
			final PainteraBaseView viewer,
			final String projectDirectory) throws Exception {
		final LabelSourceState<D, T> rep = dataset.getLabels(
				name,
				viewer.getQueue(),
				viewer.getQueue().getNumPriorities() - 1,
				viewer.viewer3D().meshesGroup(),
				viewer.getMeshManagerExecutorService(),
				viewer.getMeshWorkerExecutorService(),
				projectDirectory
		);
		final Object meta = dataset.metaData();
		LOG.debug("Adding label source with meta={}", meta);
		InvokeOnJavaFXApplicationThread.invoke(() -> viewer.addLabelSource(rep));
	}


	private static int[] blockSize(final CellGrid grid) {
		final int[] blockSize = new int[grid.numDimensions()];
		Arrays.setAll(blockSize, grid::cellDimension);
		return blockSize;
	}

	private static <C extends Cell<VolatileLabelMultisetArray>, I extends RandomAccessible<C> & IterableInterval<C>>
	Function<Long, Interval[]>[] getBlockListCaches(
			final DataSource<LabelMultisetType, ?> source,
			final ExecutorService es) {
		final int numLevels = source.getNumMipmapLevels();
		if (IntStream.range(0, numLevels).mapToObj(lvl -> source.getDataSource(
				0,
				lvl
		)).filter(src -> !(src instanceof
				AbstractCellImg<?, ?, ?, ?>)).count() > 0) {
			return null;
		}

		final int[][] blockSizes = IntStream
				.range(0, numLevels)
				.mapToObj(lvl -> (AbstractCellImg<?, ?, ?, ?>) source.getDataSource(0, lvl))
				.map(AbstractCellImg::getCellGrid)
				.map(N5OpenSourceDialog::blockSize)
				.toArray(int[][]::new);

		final double[][] scalingFactors = PainteraBaseView.scaleFactorsFromAffineTransforms(source);

		@SuppressWarnings("unchecked") final InterruptibleFunction<HashWrapper<long[]>, long[]>[] uniqueIdCaches = new
				InterruptibleFunction[numLevels];

		for (int level = 0; level < numLevels; ++level) {
			@SuppressWarnings("unchecked") final AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I>
					img =
					(AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I>) source.getDataSource(
							0,
							level
					);
			uniqueIdCaches[level] = uniqueLabelLoaders(img);
		}

		return CacheUtils.blocksForLabelCachesLongKeys(
				source,
				uniqueIdCaches,
				blockSizes,
				scalingFactors,
				CacheUtils::toCacheSoftRefLoaderCache
		);

	}

	public void setHeaderFromBackendType(String backendType)
	{
		this.setHeaderText(String.format("Open %s dataset", backendType));
	}

	private static <C extends Cell<VolatileLabelMultisetArray>, I extends RandomAccessible<C> & IterableInterval<C>>
	InterruptibleFunction<HashWrapper<long[]>, long[]> uniqueLabelLoaders(
			final AbstractCellImg<LabelMultisetType, VolatileLabelMultisetArray, C, I> img) {
		final I cells = img.getCells();
		return InterruptibleFunction.fromFunction(location -> {
			final RandomAccess<C> access = cells.randomAccess();
			access.setPosition(location.getData());
			final long[] labels = new long[]{};
			LOG.debug("Position={}: labels={}", location.getData(), labels);
			return labels;
		});
	}
}
