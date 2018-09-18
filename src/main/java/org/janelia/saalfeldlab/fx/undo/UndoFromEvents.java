package org.janelia.saalfeldlab.fx.undo;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndoFromEvents<T>
{

	// left facing triangle
	// https://www.fileformat.info/info/unicode/char/25c0/index.htm
	private static final String CURRENT_EVENT_INDICATOR = "◀";

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObservableList<Pair<T, BooleanProperty>> events;

	private final Function<T, String> title;

	private final Function<T, Node> contents;

	private final VBox eventBox = new VBox();

	private final List<Label> currentEventLabel = new ArrayList<>();

	private final IntegerProperty currentEventIndex = new SimpleIntegerProperty(-1);
	{
		currentEventIndex.addListener((obs, oldv, newv) -> {
			int oldIndex = oldv.intValue();
			int newIndex = newv.intValue();
			LOG.debug("Updating current event index {} {}", oldIndex, newIndex);
			if (oldIndex >= 0 && oldIndex < currentEventLabel.size())
				InvokeOnJavaFXApplicationThread.invoke(() -> currentEventLabel.get(oldIndex).setText(""));
			if (newIndex >= 0 && newIndex < currentEventLabel.size())
				InvokeOnJavaFXApplicationThread.invoke(() -> currentEventLabel.get(newIndex).setText(
						CURRENT_EVENT_INDICATOR));
		});
	}

	private final IntegerProperty currentEventListSize = new SimpleIntegerProperty();

	private final BooleanBinding currentIndexIsWithinList = currentEventIndex
			.greaterThanOrEqualTo(0)
			.and(currentEventIndex.lessThan(currentEventListSize));

	private final BooleanBinding canUndo = currentIndexIsWithinList;

	private final IntegerBinding redoIndex = currentEventIndex.add(1);

	private final BooleanBinding redoIndexIsWithinList = redoIndex
			.greaterThanOrEqualTo(0)
			.and(redoIndex.lessThan(currentEventListSize));

	private final BooleanBinding canRedo = redoIndexIsWithinList;

	public UndoFromEvents(
			final ObservableList<Pair<T, BooleanProperty>> events,
			final Function<T, String> title,
			final Function<T, Node> contents)
	{
		this.events = events;
		this.title = title;
		this.contents = contents;

		this.events.addListener((InvalidationListener) change -> updateEventBox(new ArrayList<>(this.events)));
		updateEventBox(new ArrayList<>(this.events));

		this.events.addListener((InvalidationListener) change -> currentEventListSize.set(this.events.size()));
		currentEventListSize.set(this.events.size());

	}

	public Node getNode()
	{
		return eventBox;
	}

	public void undo()
	{
		if (canUndo.get())
		{
			final int currentIndex = currentEventIndex.get();
			this.events.get(currentIndex).getValue().set(false);
			this.currentEventIndex.set(currentIndex-1);
		}
	}

	public void redo()
	{
		if (canRedo.get())
		{
			final int index = redoIndex.get();
			this.events.get(index).getValue().set(true);
			this.currentEventIndex.set(index);
		}
	}

	public static <T> Node withUndoRedoButtons(
			final ObservableList<Pair<T, BooleanProperty>> events,
			final Function<T, String> title,
			final Function<T, Node> contents)
	{
		final UndoFromEvents<T> undo       = new UndoFromEvents<>(events, title, contents);
		final Button            undoButton = new Button("Undo");
		final Button            redoButton = new Button("Redo");
		final Region            filler     = new Region();
		HBox                    buttonBox  = new HBox(filler, undoButton, redoButton);
		final TitledPane        tp         = new TitledPane("Events", undo.getNode());

		undoButton.setOnAction(e -> undo.undo());
		redoButton.setOnAction(e -> undo.redo());

		undo.canUndo.addListener((obs, oldv, newv) -> undoButton.setDisable(!newv));
		undo.canRedo.addListener((obs, oldv, newv) -> redoButton.setDisable(!newv));
		undoButton.setDisable(!undo.canUndo.get());
		redoButton.setDisable(!undo.canRedo.get());

		tp.setExpanded(false);

		return new VBox(buttonBox, undo.getNode());
	}

	private void updateEventBox(List<Pair<T, ? extends BooleanProperty>> events)
	{
		LOG.debug("Updating event box for events {}", events);
		List<Node> nodes = new ArrayList<>();
		this.currentEventLabel.clear();
		this.currentEventIndex.set(-1);
		for (int i = 0; i < events.size(); ++i)
		{
			final Pair<T, ? extends BooleanProperty> event             = events.get(i);
			final String                             title             = this.title.apply(event.getKey());
			final Node                               contents          = this.contents.apply(event.getKey());
			final CheckBox                           cbox              = new CheckBox(null);
			final Label                              currentEventLabel = new Label("");

			cbox.selectedProperty().bindBidirectional(event.getValue());
			currentEventLabel.setMinWidth(30);
			currentEventLabel.setMaxWidth(30);
			currentEventLabel.setPrefWidth(30);

			final TitledPane tp = new TitledPane(title, contents);
			tp.setGraphic(new HBox(cbox, currentEventLabel));
			tp.setExpanded(false);

			this.currentEventLabel.add(currentEventLabel);
			nodes.add(tp);
		}
		this.currentEventIndex.set(events.size() - 1);
		Collections.reverse(nodes);
		InvokeOnJavaFXApplicationThread.invoke(() -> this.eventBox.getChildren().setAll(nodes));
	}

}
