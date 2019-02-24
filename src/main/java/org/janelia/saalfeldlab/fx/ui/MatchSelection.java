package org.janelia.saalfeldlab.fx.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.Separators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Menus cannot be updated dynamically. Use custom node for (fuzzy) filtering
 *
 * https://stackoverflow.com/questions/54834206/javafx-dynamically-update-menu-while-showing
 * https://bugs.openjdk.java.net/browse/JDK-8219620
 */
public class MatchSelection extends Region {

	private static final class FuzzyMatcher implements BiFunction<String, List<String>, List<String>> {

		private final BiFunction<String, List<String>, List<ExtractedResult>> matcher;

		private FuzzyMatcher(BiFunction<String, List<String>, List<ExtractedResult>> matcher) {
			this.matcher = matcher;
		}

		@Override
		public List<String> apply(String query, List<String> from) {
			return matcher
					.apply(query, from)
					.stream()
					.map(ExtractedResult::getString)
					.collect(Collectors.toList());
		}
	}

	public static MatchSelection fuzzySorted(final List<String> candidates, final Consumer<String> onConfirm) {
		return new MatchSelection(candidates, new FuzzyMatcher(FuzzySearch::extractSorted), onConfirm);
	}

	public static MatchSelection fuzzySorted(final List<String> candidates, final Consumer<String> onConfirm, final IntSupplier cutoff) {
		return new MatchSelection(candidates, new FuzzyMatcher((query, from) -> FuzzySearch.extractSorted(query, from, cutoff.getAsInt())), onConfirm);
	}

	public static MatchSelection fuzzyTop(final List<String> candidates, final Consumer<String> onConfirm, final IntSupplier limit) {
		return new MatchSelection(candidates, new FuzzyMatcher((query, from) -> FuzzySearch.extractTop(query, from, limit.getAsInt())), onConfirm);
	}

	public static MatchSelection fuzzyTop(final List<String> candidates, final Consumer<String> onConfirm, final IntSupplier limit, final IntSupplier cutoff) {
		return new MatchSelection(candidates, new FuzzyMatcher((query, from) -> FuzzySearch.extractTop(query, from, limit.getAsInt(), cutoff.getAsInt())), onConfirm);
	}

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final List<String> candidates;

	private final BiFunction<String, List<String>, List<String>> matcher;

	private final Consumer<String> onConfirm;

	private final IntegerProperty currentSelection = new SimpleIntegerProperty(this, "current selection", 0);

	private final Background defaultLabelBackground = new Label().getBackground();

	private final Background highlightLabelBackground = new Background(new BackgroundFill(Color.GRAY.brighter(), CornerRadii.EMPTY, Insets.EMPTY));

	private final TextField fuzzySearchField = new TextField(null);

	public MatchSelection(
			final List<String> candidates,
			final BiFunction<String, List<String>, List<String>> matcher,
			final Consumer<String> onConfirm) {
		this.candidates = Collections.unmodifiableList(candidates);
		this.matcher = matcher;
		this.onConfirm = onConfirm;
		super.getChildren().setAll(makeNode());
		this.fuzzySearchField.maxWidthProperty().bind(maxWidthProperty());
		this.focusedProperty().addListener((obs, oldv, newv) -> {if (newv != null && newv) this.fuzzySearchField.requestFocus();});
	}

	private Node makeNode() {
		fuzzySearchField.setTooltip(new Tooltip("Type to filter (fuzzy matching)"));
		fuzzySearchField.setPromptText("Type to filter");

		fuzzySearchField.addEventFilter(MouseEvent.MOUSE_MOVED, e->{fuzzySearchField.requestFocus(); fuzzySearchField.selectEnd();});
		final ObservableList<String> currentOrder = FXCollections.observableArrayList();
		fuzzySearchField.textProperty().addListener((obs, oldv, newv) -> currentOrder.setAll(newv == null || newv.length() == 0 ? candidates : matcher.apply(newv, candidates)));
		currentSelection.addListener((obs, oldv, newv) -> {
			LOG.debug("Updating current selection from {} to {}", oldv, newv);
			if (currentOrder.size() > 0) {
				if (newv.intValue() < 0)
					currentSelection.setValue(currentOrder.size() - Math.min(currentOrder.size(), Math.abs(newv.intValue())));
				else if (newv.intValue() >= currentOrder.size())
					currentSelection.setValue(currentOrder.size() - 1);
			}
		});

		final VBox labelBox = new VBox();
		currentOrder.addListener((ListChangeListener<String>) change -> {
			final ArrayList<String> copy = new ArrayList<>(currentOrder);
			final ArrayList<Label> labels = new ArrayList<>();
			for (int i = 0; i < copy.size(); ++i) {
				final String text = copy.get(i);
				final int fi = i;
				labels.add(toLabel(
						text,
						() -> currentSelection.set(fi),
						() -> currentSelection.set(0),
						() -> onConfirm.accept(text),
						() -> currentSelection.set(fi)));
			}
			labelBox.getChildren().setAll(labels);
			currentSelection.set(-1);
			currentSelection.set(0);
		});

		currentSelection.addListener((obs, oldv, newv) -> {
			LOG.debug("Updating current selection from {} to {}", oldv, newv)	;
			final ObservableList<Node> items = labelBox.getChildren();
			final int newIndex = newv.intValue();
			final int oldIndex = oldv.intValue();
			if (newIndex >= 0 && newIndex < items.size())
				((Label)items.get(newIndex)).setBackground(highlightLabelBackground);
			if (oldIndex >= 0 && oldIndex < items.size())
				((Label)items.get(oldIndex)).setBackground(defaultLabelBackground);
		});

		currentSelection.set(-1);
		fuzzySearchField.setText("");

		final VBox contents = new VBox(fuzzySearchField, Separators.horizontal(), labelBox);
		contents.setOnKeyPressed(e -> {
			LOG.debug("Key pressed in contents with code {}", e.getCode());
			switch (e.getCode()) {
				case ESCAPE:
					if (Optional.ofNullable(fuzzySearchField.getText()).filter(s -> s.length() > 0).isPresent()) {
						fuzzySearchField.setText("");
						e.consume();
					}
					break;
				case ENTER:
					final int selection = currentSelection.get();
					onConfirm.accept(selection < 0 || selection >= currentOrder.size() ? null : currentOrder.get(selection));
					e.consume();
					break;
				case DOWN:
					currentSelection.set(currentSelection.getValue() + 1);
					e.consume();
					break;
				case UP:
					currentSelection.set(currentSelection.getValue() - 1);
					e.consume();
					break;
				default:
					break;
			}
		});
		fuzzySearchField.setOnKeyPressed(e -> {
			LOG.debug("Key pressed in fuzzy search field with code {}", e.getCode());
			switch (e.getCode()) {
				case DOWN:
					currentSelection.set(currentSelection.getValue() + 1);
					e.consume();
					break;
				case UP:
					currentSelection.set(currentSelection.getValue() - 1);
					e.consume();
					break;
				default:
					break;
			}
		});
		contents.focusedProperty().addListener((obs, oldv, newv) -> {if (newv != null && newv) fuzzySearchField.requestFocus();});
		return contents;
	}

	/**
	 *
	 * @return {@link Region#getChildrenUnmodifiable()}
	 */
	@Override
	public ObservableList<Node> getChildren() {
		return super.getChildrenUnmodifiable();
	}

	private Label toLabel(
			final String text,
			final Runnable onMouseEntered,
			final Runnable onMouseExited,
			final Runnable onMousePressed,
			final Runnable onMouseMoved) {
		final Label label = Labels.withTooltip(text);
		label.setOnMouseEntered(e -> onMouseEntered.run());
		label.setOnMouseExited(e -> onMouseExited.run());
		label.setOnMouseMoved(e -> onMouseMoved.run());
		label.setOnMousePressed(e -> {
			if (e.isPrimaryButtonDown()) {
				onMousePressed.run();
				e.consume();
			}
		});
		label.maxWidthProperty().bind(maxWidthProperty());
		return label;
	}

}
