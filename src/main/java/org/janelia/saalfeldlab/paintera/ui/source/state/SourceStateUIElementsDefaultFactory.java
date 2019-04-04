package org.janelia.saalfeldlab.paintera.ui.source.state;

import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.imglib2.converter.Converter;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.undo.UndoFromEvents;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentStateWithActionTracker;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Detach;
import org.janelia.saalfeldlab.paintera.control.assignment.action.Merge;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.state.ThresholdingSourceState;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.janelia.saalfeldlab.paintera.ui.source.MaskedSourcePane;
import org.janelia.saalfeldlab.paintera.ui.source.axisorder.AxisOrderPane;
import org.janelia.saalfeldlab.paintera.ui.source.composite.CompositePane;
import org.janelia.saalfeldlab.paintera.ui.source.converter.ConverterPane;
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshPane;
import org.janelia.saalfeldlab.util.SciJavaUtils;
import org.scijava.InstantiableException;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SourceStateUIElementsDefaultFactory implements SourceStateUIElementsFactory<SourceState> {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static Map<Class<?>, BindUnbindForConverterFactory> CONVERTER_SUPPLIER_FACTORIES_MAP = null;

	private static BindUnbindForConverterFactory getConverterSupplierFactory(Class<?> clazz)
	{
		if (CONVERTER_SUPPLIER_FACTORIES_MAP == null)
		{
			final Map<Class<?>, BindUnbindForConverterFactory> tmp = new HashMap<>();
			try {
				SciJavaUtils.byTargetClassSortedByPriorities(BindUnbindForConverterFactory.class)
						.entrySet()
						.stream()
						.filter(Objects::nonNull)
						.filter(e -> e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty())
						.forEach(e -> tmp.put(e.getKey(), e.getValue().get(0).getKey()));
			} catch (InstantiableException e) {
				throw new RuntimeException(e);
			}
			CONVERTER_SUPPLIER_FACTORIES_MAP = new HashMap<>(tmp);
		}
		return CONVERTER_SUPPLIER_FACTORIES_MAP.get(clazz);
	}


	@Override
	public Class<SourceState> getTargetClass() {
		return SourceState.class;
	}

	public interface AdditionalBindUnbindSuppliersFactory<T extends SourceState> extends SciJavaPlugin, SciJavaUtils.HasTargetClass<T>
	{

		BindUnbindAndNodeSupplier[] create(T state);

	}

	public interface BindUnbindForConverterFactory<T extends Converter> extends SciJavaPlugin, SciJavaUtils.HasTargetClass<T>
	{

		BindUnbindAndNodeSupplier create(T converter);

	}

	@Plugin(type = AdditionalBindUnbindSuppliersFactory.class)
	public static class LabelSourceStateAdditionalBindAndUnbindSupplierFactory implements AdditionalBindUnbindSuppliersFactory<LabelSourceState>
	{

		@Override
		public BindUnbindAndNodeSupplier[] create(LabelSourceState state) {
			return new BindUnbindAndNodeSupplier[] {
					selectedIds(state),
					meshPane(state),
					assignmentPane(state),
					state.getDataSource() instanceof MaskedSource<?, ?>
							? new MaskedSourcePane((MaskedSource<?, ?>) state.getDataSource())
							: BindUnbindAndNodeSupplier.empty()
			};
		}

		@Override
		public Class<LabelSourceState> getTargetClass() {
			return LabelSourceState.class;
		}
	}

	@Plugin(type = AdditionalBindUnbindSuppliersFactory.class)
	public static class ThreshodlingSourceStateBindAndUNbindSupplierFactory implements AdditionalBindUnbindSuppliersFactory<ThresholdingSourceState<?, ?>> {

		@Override
		public BindUnbindAndNodeSupplier[] create(ThresholdingSourceState<?, ?> state) {

			final Supplier<Node> converter = () -> {
				final CheckBox checkBox = new CheckBox("From Raw Source");
				checkBox.setTooltip(new Tooltip(
						"When selected, use min and max from underlying raw source for threshold, " +
						"use min and max below otherwise."));
				checkBox.setSelected(!state.controlSeparatelyProperty().get());
				checkBox.selectedProperty().addListener((obs, oldv, newv) -> state.controlSeparatelyProperty().set(!newv));
				// TODO should we also listen on state.controLSeparatelyProperty?
				// TODO currently, no other way to change it, so maybe not (for now)
				final NumberField<DoubleProperty> min = NumberField.doubleField(state.minProperty().get(), d -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
				final NumberField<DoubleProperty> max = NumberField.doubleField(state.maxProperty().get(), d -> true, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
				min.valueProperty().addListener((obs, oldv, newv) -> state.minProperty().set(newv.doubleValue()));
				max.valueProperty().addListener((obs, oldv, newv) -> state.maxProperty().set(newv.doubleValue()));
				final GridPane minMax = new GridPane();
				minMax.add(new Label("min"), 0, 0);
				minMax.add(new Label("max"), 0, 1);
				minMax.add(min.textField(), 1, 0);
				minMax.add(max.textField(), 1, 1);
				GridPane.setHgrow(min.textField(), Priority.ALWAYS);
				GridPane.setHgrow(max.textField(), Priority.ALWAYS);
				return TitledPanes.createCollapsed("Threshold", new VBox(checkBox, minMax));
			};
			return new BindUnbindAndNodeSupplier[] {BindUnbindAndNodeSupplier.noBind(converter)};
		}

		@Override
		public Class<ThresholdingSourceState<?, ?>> getTargetClass() {
			return (Class<ThresholdingSourceState<?, ?>>) (Class) ThresholdingSourceState.class;
		}
	}


	public BindUnbindAndNodeSupplier create(SourceState state)
	{
		BindUnbindAndNodeSupplier[] suppliers = {
				new AxisOrderPane(state.axisOrderProperty()),
				new CompositePane(state.compositeProperty()),
				fromConverter(state.converter())
		};

		try {
			final BindUnbindAndNodeSupplier[] additionalSuppliers = Optional
					.ofNullable(SciJavaUtils.byTargetClassSortedByPriorities(AdditionalBindUnbindSuppliersFactory.class).get(state.getClass()))
					.filter(l -> !l.isEmpty())
					.map(l -> l.get(0))
					.map(f -> f.getKey().create(state))
					.orElseGet(() -> new BindUnbindAndNodeSupplier[]{BindUnbindAndNodeSupplier.empty()});

			final BindUnbindAndNodeSupplier[] allSuppliers = Stream
					.of(suppliers, additionalSuppliers)
					.flatMap(Arrays::stream)
					.toArray(BindUnbindAndNodeSupplier[]::new);

			return new BindUnbindAndNodeSupplierImpl(allSuppliers);
		} catch (InstantiableException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static BindUnbindAndNodeSupplier fromConverter(Converter converter)
	{
		LOG.debug("Getting supplier for converter {}", converter);
		return Optional
				.ofNullable(getConverterSupplierFactory(converter.getClass()))
				.map(s -> (Function<Converter, BindUnbindAndNodeSupplier>) (s::create))
				.orElse((Function<Converter, BindUnbindAndNodeSupplier>) ConverterPane::new)
				.apply(converter);
	}

	private static class BindUnbindAndNodeSupplierImpl implements BindUnbindAndNodeSupplier
	{

		private final Collection<BindUnbindAndNodeSupplier> suppliers;

		private final Node node;

		private BindUnbindAndNodeSupplierImpl(final BindUnbindAndNodeSupplier... suppliers) {
			this(Arrays.asList(suppliers));
		}

		private BindUnbindAndNodeSupplierImpl(final Collection<BindUnbindAndNodeSupplier> suppliers) {
			this.suppliers = suppliers;
			this.node = new VBox(suppliers.stream().map(BindUnbindAndNodeSupplier::get).toArray(Node[]::new));
		}

		@Override
		public Node get() {
			return this.node;
		}

		@Override
		public void bind() {
			this.suppliers.forEach(BindUnbindAndNodeSupplier::bind);
		}

		@Override
		public void unbind() {
			this.suppliers.forEach(BindUnbindAndNodeSupplier::unbind);
		}
	}

	private static BindUnbindAndNodeSupplier selectedIds(final LabelSourceState<?, ?> state)
	{
		final SelectedIds selectedIds = state.selectedIds();
		final FragmentSegmentAssignmentState assignment  = state.assignment();

		if (selectedIds == null || assignment == null) { return BindUnbindAndNodeSupplier.empty(); }

		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);

		final TextField lastSelectionField    = new TextField();
		final TextField selectedIdsField      = new TextField();
		final TextField selectedSegmentsField = new TextField();
		final GridPane grid                  = new GridPane();
		grid.setHgap(5);

		grid.add(lastSelectionField, 1, 0);
		grid.add(selectedIdsField, 1, 1);
		grid.add(selectedSegmentsField, 1, 2);

		final Label lastSelectionLabel = new Label("Active");
		final Label fragmentLabel      = new Label("Fragments");
		final Label segmentLabel       = new Label("Segments");
		lastSelectionLabel.setTooltip(new Tooltip("Active (last actiated) fragment id"));
		fragmentLabel.setTooltip(new Tooltip("Active fragment ids"));
		segmentLabel.setTooltip(new Tooltip("Active segment ids"));

		final Tooltip activeFragmentsToolTip = new Tooltip();
		final Tooltip activeSegmentsToolTip  = new Tooltip();
		activeFragmentsToolTip.textProperty().bind(selectedIdsField.textProperty());
		activeSegmentsToolTip.textProperty().bind(selectedSegmentsField.textProperty());
		selectedIdsField.setTooltip(activeFragmentsToolTip);
		selectedSegmentsField.setTooltip(activeSegmentsToolTip);

		grid.add(lastSelectionLabel, 0, 0);
		grid.add(fragmentLabel, 0, 1);
		grid.add(segmentLabel, 0, 2);

		GridPane.setHgrow(lastSelectionField, Priority.ALWAYS);
		GridPane.setHgrow(selectedIdsField, Priority.ALWAYS);
		GridPane.setHgrow(selectedSegmentsField, Priority.ALWAYS);
		lastSelectionField.setEditable(false);
		selectedIdsField.setEditable(false);
		selectedSegmentsField.setEditable(false);

		final Button modifyButton = new Button("Modify selection");
		modifyButton.setOnAction(event -> {

			event.consume();
			final TextField selField = new TextField();
			final TextField lsField  = new TextField();
			selField.setText(selectedIdsField.getText());
			lsField.setText(lastSelectionField.getText());

			final Dialog<ButtonType> dialog = new Dialog<>();
			dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

			final GridPane dialogGrid = new GridPane();
			dialogGrid.setHgap(5);

			final Label lsSelectionLabel = new Label("Active");
			final Label selLabel         = new Label("Fragments");

			GridPane.setHgrow(selField, Priority.ALWAYS);
			GridPane.setHgrow(lsField, Priority.ALWAYS);

			dialogGrid.add(lsField, 1, 0);
			dialogGrid.add(selField, 1, 1);

			dialogGrid.add(lsSelectionLabel, 0, 0);
			dialogGrid.add(selLabel, 0, 1);

			dialog.getDialogPane().setContent(dialogGrid);

			if (dialog.showAndWait().map(ButtonType.OK::equals).orElse(false))
			{
				try
				{
					final long[] userSelection =
							Arrays
									.stream(selField.getText().split(","))
									.map(String::trim)
									.mapToLong(Long::parseLong)
									.toArray();
					final TLongHashSet invalidSelection = new TLongHashSet(Arrays
							.stream(userSelection)
							.filter(((LongPredicate) state.idService()::isInvalidated).negate())
							.toArray());
					final long lastSelection = Optional.ofNullable(lsField.getText()).filter(t -> t.length() > 0).map(
							Long::parseLong).orElse(-1L);
					if (!state.idService().isInvalidated(lastSelection))
					{
						invalidSelection.add(lastSelection);
					}

					if (invalidSelection.size() > 0)
					{
						final Alert invalidSelectionAlert = new Alert(Alert.AlertType.ERROR);
						invalidSelectionAlert.setTitle("Paintera");
						invalidSelectionAlert.setContentText(
								"Unable to set selection - ids out of range for id service. For new id, press N.");
						invalidSelectionAlert.getDialogPane().setExpandableContent(new VBox(
								1.0,
								Arrays.stream(invalidSelection.toArray()).mapToObj(Long::toString).map(Label::new)
										.toArray(
												Label[]::new)
						));
						invalidSelectionAlert.show();

					}
					else
					{
						selectedIds.activate(userSelection);
						if (userSelection.length > 0 && net.imglib2.type.label.Label.regular(lastSelection))
						{
							selectedIds.activateAlso(lastSelection);
						}
					}
				} catch (final NumberFormatException e)
				{
					LOG.error(
							"Invalid user input (only number (active) or comma separated numbers (fragments) allowed " +
									"-- not updating selection");
				}

			}
		});

		final VBox vbox = new VBox(
				grid,
				modifyButton
		);

		final TitledPane contents = new TitledPane("Selection", vbox);
		contents.setExpanded(false);

		final InvalidationListener selectionListener = obs -> {
			final long[] selIds = selectedIds.getActiveIds();

			lastSelectionField.setText(Long.toString(selectedIds.getLastSelection()));

			if (selIds.length < 1)
			{
				selectedIdsField.setText("");
				return;
			}

			final StringBuilder sb = new StringBuilder().append(selIds[0]);
			for (int i = 1; i < selIds.length; ++i)
			{
				sb
						.append(", ")
						.append(selIds[i]);
			}

			selectedIdsField.setText(sb.toString());
		};

		final InvalidationListener selectedSegmentsListener = obs -> {
			final long[] selIds = selectedSegments.getSelectedSegments();

			if (selIds.length < 1)
			{
				selectedSegmentsField.setText("");
				return;
			}

			final StringBuilder sb = new StringBuilder().append(selIds[0]);
			for (int i = 1; i < selIds.length; ++i)
			{
				sb
						.append(", ")
						.append(selIds[i]);
			}

			selectedSegmentsField.setText(sb.toString());
		};

		return new BindUnbindAndNodeSupplier()
		{

			@Override
			public void unbind()
			{
				selectedIds.removeListener(selectionListener);
				selectedSegments.removeListener(selectedSegmentsListener);
			}

			@Override
			public void bind()
			{
				selectedIds.addListener(selectionListener);
				selectedSegments.addListener(selectedSegmentsListener);
			}

			@Override
			public Node get()
			{
				return contents;
			}
		};

	}

	private static BindUnbindAndNodeSupplier meshPane(final LabelSourceState<?, ?> state)
	{
		final FragmentSegmentAssignmentState  assignment       = state.assignment();
		final SelectedIds                     selectedIds      = state.selectedIds();
		final SelectedSegments                selectedSegments = new SelectedSegments(selectedIds, assignment);
		final MeshManager<Long, TLongHashSet> meshManager      = state.meshManager();
		final ManagedMeshSettings meshSettings     = state.managedMeshSettings();
		final int                             numScaleLevels   = state.dataSource().getNumMipmapLevels();
		final MeshInfos<TLongHashSet> meshInfos        = new MeshInfos<>(
				selectedSegments,
				assignment,
				meshManager,
				meshSettings,
				numScaleLevels
		);
		LOG.debug(
				"Creating mesh pane for source {} from {} and {}: ",
				state.nameProperty().get(),
				meshManager,
				meshInfos
		);
		if (meshManager != null)
		{
			return new MeshPane(
					meshManager,
					meshInfos,
					numScaleLevels
			);
		}
		return BindUnbindAndNodeSupplier.empty();
	}


	private static BindUnbindAndNodeSupplier assignmentPane(final LabelSourceState<?, ?> state)
	{
		final FragmentSegmentAssignmentState assignments = state.assignment();
		if (assignments instanceof FragmentSegmentAssignmentStateWithActionTracker)
		{
			final Function<AssignmentAction, String> title = action -> {
				switch(action.getType())
				{
					case MERGE:
						final Merge m = (Merge)action;
						return String.format("M: %d %d (%d)", m.fromFragmentId, m.intoFragmentId, m.fromFragmentId);
					case DETACH:
						final Detach d = (Detach) action;
						return String.format("D: %d %d", d.fragmentId, d.fragmentFrom);
					default:
						return "UNSUPPORTED ACTION";
				}
			};
			final Function<AssignmentAction, Node> contents = action -> Labels.withTooltip(action.toString());
			final FragmentSegmentAssignmentStateWithActionTracker assignmentsWithHistory = (FragmentSegmentAssignmentStateWithActionTracker) assignments;
			Supplier<Node> pane = () -> TitledPanes.createCollapsed("Assignments", UndoFromEvents.withUndoRedoButtons(
					assignmentsWithHistory.events(),
					title,
					contents
			));
			return BindUnbindAndNodeSupplier.noBind(pane);
		}
		return BindUnbindAndNodeSupplier.empty();
	}

}
