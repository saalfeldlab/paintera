package org.janelia.saalfeldlab.paintera.ui.source.state;

import bdv.viewer.Source;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.janelia.saalfeldlab.paintera.ui.CloseButton;
import org.janelia.saalfeldlab.util.SciJavaUtils;
import org.scijava.InstantiableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class StatePane implements BindUnbindAndNodeSupplier
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static Map<Class<?>, SourceStateUIElementsFactory> UI_ELEMENTS_FACTORY_MAP = null;

	private static SourceStateUIElementsFactory getUiElementsFactory(Class<?> clazz)
	{
		if (UI_ELEMENTS_FACTORY_MAP == null)
		{
			final Map<Class<?>, SourceStateUIElementsFactory> tmp = new HashMap<>();
			try {
				SciJavaUtils.byTargetClassSortedByPriorities(SourceStateUIElementsFactory.class)
						.entrySet()
						.stream()
						.filter(Objects::nonNull)
						.filter(e -> e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty())
						.forEach(e -> tmp.put(e.getKey(), e.getValue().get(0).getKey()));
			} catch (InstantiableException e) {
				throw new RuntimeException(e);
			}
			UI_ELEMENTS_FACTORY_MAP = new HashMap<>(tmp);
		}
		return UI_ELEMENTS_FACTORY_MAP.get(clazz);
	}

	private final SourceState<?, ?> state;

	private final SourceInfo sourceInfo;

	private final BindUnbindAndNodeSupplier supplier;

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
		this.supplier = Optional
				.ofNullable(getUiElementsFactory(state.getClass()))
				.orElseGet(SourceStateUIElementsDefaultFactory::new)
				.create(state);

		this.statePane = new TitledPane(null, this.supplier.get());
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
		this.supplier.bind();
	}

	@Override
	public void unbind()
	{
		this.name.unbindBidirectional(state.nameProperty());
		this.isVisible.unbindBidirectional(state.isVisibleProperty());
		this.isCurrentSource.unbind();
		this.supplier.unbind();
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

}
