package org.janelia.saalfeldlab.paintera.ui.source.state;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import bdv.viewer.Source;
import gnu.trove.set.hash.TLongHashSet;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentState;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.meshes.MeshInfos;
import org.janelia.saalfeldlab.paintera.meshes.MeshManager;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.janelia.saalfeldlab.paintera.ui.CloseButton;
import org.janelia.saalfeldlab.paintera.ui.source.composite.CompositePane;
import org.janelia.saalfeldlab.paintera.ui.source.converter.ConverterPane;
import org.janelia.saalfeldlab.paintera.ui.source.mesh.MeshPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatePane implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final SourceState<?, ?> state;

	private final SourceInfo sourceInfo;

	private final BindUnbindAndNodeSupplier[] children;

	private final TitledPane statePane;

	private final StringProperty name = new SimpleStringProperty();

	private final BooleanProperty isCurrentSource = new SimpleBooleanProperty();

	private final BooleanProperty isVisible = new SimpleBooleanProperty();

	public StatePane(
			final SourceState<?, ?> state,
			final SourceInfo sourceInfo,
			final Consumer<Source<?>> remove,
			final ObservableDoubleValue width)
	{
		super();
		this.state = state;
		this.sourceInfo = sourceInfo;
		this.children = new BindUnbindAndNodeSupplier[] {
				new CompositePane(state.compositeProperty()),
				new ConverterPane(state.converter()),
				state instanceof LabelSourceState<?, ?>
				? selectedIds((LabelSourceState<?, ?>) state)
				: BindUnbindAndNodeSupplier.empty(),
				state instanceof LabelSourceState<?, ?>
				? meshPane((LabelSourceState<?, ?>) state)
				: BindUnbindAndNodeSupplier.empty()
		};

		final VBox contents = new VBox(Arrays.stream(this.children).map(c -> c.get()).toArray(Node[]::new));
		this.statePane = new TitledPane(null, contents);
		this.statePane.minWidthProperty().bind(width);
		this.statePane.maxWidthProperty().bind(width);
		this.statePane.prefWidthProperty().bind(width);
		this.statePane.setExpanded(false);

		// create graphics for titled pane
		final Node closeButton = CloseButton.create(8);
		closeButton.setOnMousePressed(event -> remove.accept(state.getDataSource()));
		final Label sourceElementLabel = new Label(state.nameProperty().get(), closeButton);
		sourceElementLabel.textProperty().bind(this.name);
		sourceElementLabel.setOnMouseClicked(event -> {
			event.consume();
			if (event.getClickCount() != 2) { return; }
			final Dialog<Boolean> d = new Dialog<>();
			d.setTitle("Set source name");
			final TextField tf = new TextField(name.get());
			tf.setPromptText("source name");
			d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			d.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(tf.textProperty().isNull().or(tf
					.textProperty().length().isEqualTo(
					0)));
			d.setGraphic(tf);
			d.setResultConverter(ButtonType.OK::equals);
			final Optional<Boolean> result = d.showAndWait();
			if (result.isPresent() && result.get())
			{
				name.set(tf.getText());
			}
		});
		sourceElementLabel.setContentDisplay(ContentDisplay.RIGHT);
		sourceElementLabel.underlineProperty().bind(isCurrentSource);

		final HBox sourceElementButtons = getPaneGraphics(isVisible);
		sourceElementButtons.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(sourceElementButtons, Priority.ALWAYS);
		final HBox graphic = new HBox(sourceElementButtons, sourceElementLabel);
		graphic.setSpacing(20);
		//		graphic.prefWidthProperty().bind( this.width.multiply( 0.8 ) );
		this.statePane.setGraphic(graphic);
		//		addDragAndDropListener( statePane, this.info, contents.getChildren() );
	}

	@Override
	public Node get()
	{
		return this.statePane;
	}

	@Override
	public void bind()
	{
		this.name.bindBidirectional(state.nameProperty());
		this.isVisible.bindBidirectional(state.isVisibleProperty());
		this.isCurrentSource.bind(sourceInfo.isCurrentSource(state.getDataSource()));
		Arrays.stream(children).forEach(BindUnbindAndNodeSupplier::bind);
	}

	@Override
	public void unbind()
	{
		this.name.unbindBidirectional(state.nameProperty());
		this.isVisible.unbindBidirectional(state.isVisibleProperty());
		this.isCurrentSource.unbind();
		Arrays.stream(children).forEach(BindUnbindAndNodeSupplier::unbind);
	}

	private static HBox getPaneGraphics(final BooleanProperty isVisible)
	{
		final CheckBox cb = new CheckBox();
		cb.setMaxWidth(20);
		cb.selectedProperty().bindBidirectional(isVisible);
		cb.selectedProperty().set(isVisible.get());
		final HBox tp = new HBox(cb);
		return tp;
	}

	private static BindUnbindAndNodeSupplier selectedIds(final LabelSourceState<?, ?> state)
	{
		final SelectedIds                    selectedIds = state.selectedIds();
		final FragmentSegmentAssignmentState assignment  = state.assignment();

		if (selectedIds == null || assignment == null) { return BindUnbindAndNodeSupplier.empty(); }

		final SelectedSegments selectedSegments = new SelectedSegments(selectedIds, assignment);

		final TextField lastSelectionField    = new TextField();
		final TextField selectedIdsField      = new TextField();
		final TextField selectedSegmentsField = new TextField();
		final GridPane  grid                  = new GridPane();
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
						final Alert invalidSelectionAlert = new Alert(AlertType.ERROR);
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
		final ManagedMeshSettings             meshSettings     = state.managedMeshSettings();
		final int                             numScaleLevels   = state.dataSource().getNumMipmapLevels();
		final MeshInfos<TLongHashSet>         meshInfos        = new MeshInfos<>(
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

	//	private static void addDragAndDropListener( final Node p, final SourceInfo info, final List< Node > children )
	//	{
	//		p.setOnDragDetected( event -> {
	//			p.startFullDrag();
	//		} );
	//
	//		p.setOnMouseDragReleased( event -> {
	//			final Object origin = event.getGestureSource();
	//			if ( origin != p && origin instanceof TitledPane )
	//			{
	//				final TitledPane pane = ( TitledPane ) origin;
	//				final int sourceIndex = children.indexOf( pane );
	//				final int targetIndex = children.indexOf( p );
	//				info.moveSourceTo( sourceIndex, targetIndex );
	//			}
	//		} );
	//	}

}
