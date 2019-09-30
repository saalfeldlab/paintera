package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import me.xdrop.fuzzywuzzy.Applicable;
import me.xdrop.fuzzywuzzy.algorithms.WeightedRatio;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.MarkdownPane;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BookmarkSelectionDialog extends Alert {

	private final List<BookmarkConfig.Bookmark> bookmarks;

	private final ObservableList<BookmarkConfig.Bookmark> sortedBookmarks;

	private final TextField fuzzySearchField = new TextField(null);

	private final ScrollPane selectionPane = new ScrollPane();

	private final ObjectProperty<BookmarkConfig.Bookmark> selectedBookmark = new SimpleObjectProperty<>();

	public BookmarkSelectionDialog(final Collection<? extends BookmarkConfig.Bookmark> bookmarks) {
		super(AlertType.CONFIRMATION);
		this.bookmarks = new ArrayList<>(bookmarks);
		this.sortedBookmarks = FXCollections.observableArrayList();

		this.sortedBookmarks.addListener((ListChangeListener<BookmarkConfig.Bookmark>) c -> {
			final List<BookmarkConfig.Bookmark> bookmarksInput = new ArrayList<>(c.getList());
			final GridPane grid = new GridPane();
			for (int i = 0; i < bookmarksInput.size(); ++i) {
				final BookmarkConfig.Bookmark bm = bookmarksInput.get(i);
				final int iOne = i + 1;

				final Button idButton = new Button(i < 9 ? "_" + iOne : i == 9 ? "1_0" : Integer.toString(iOne));
				idButton.setOnAction(e -> triggerOkWithBookmark(bm));
				idButton.setPrefWidth(30.0);

				final String labelText = bm.getNote() == null ? "" : bm.getNote();
//				final Label noteLabel = Labels.withTooltip(labelText.replace("\n", " "), labelText);
				final MarkdownPane note = new MarkdownPane();
				note.setEditable(false);
				note.setText(labelText);
				note.showRenderedTab();
				final TitledPane tp = TitledPanes.createCollapsed(labelText.replace("\n", " "), note);

				grid.add(idButton, 0, i);
				grid.add(tp, 1, i);
				GridPane.setHgrow(tp, Priority.ALWAYS);
				GridPane.setValignment(idButton, VPos.TOP);
			}
			selectionPane.setContent(grid);
		});

		fuzzySearchField.setPromptText("Type to fuzzy search");
		fuzzySearchField.textProperty().addListener((obs, oldv, newv) -> {
			if (newv == null || newv.length() == 0) {
				this.sortedBookmarks.setAll(this.bookmarks);
			} else {
				// TODO actually do the sorting
				final List<BookmarkWithFuzzyScore> scoredBookmarks = this
						.bookmarks
						.stream()
						.map(bm -> new BookmarkWithFuzzyScore(bm, newv))
						.sorted(Collections.reverseOrder())
						.collect(Collectors.toList());
				this.sortedBookmarks.setAll(scoredBookmarks
						.stream()
						.map(BookmarkWithFuzzyScore::getBookmark)
						.collect(Collectors.toList()));
			}
		});

		getDialogPane().setContent(new VBox(fuzzySearchField, selectionPane));
		getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			if (new KeyCodeCombination(KeyCode.ENTER).match(e)) {
				e.consume();
				triggerOkWithBookmark(this.sortedBookmarks.isEmpty() ? null : this.sortedBookmarks.get(0));
			}
		});

		setTitle(Paintera.NAME);
		setGraphic(null);
		setHeaderText("Go to bookmark: " +
				"Type into text field to sort by fuzzy score of bookmark notes. " +
				"Click the button next to the description to select a bookmark. " +
				"Alternatively, use Alt+<N> to select from the first ten matches. " +
				"Click \"Ok\" to apply the best match (bookmark 1) or \"Cancel\" to return to current state.");
		setResizable(true);
		((Button)getDialogPane().lookupButton(ButtonType.OK)).setText("_Ok");
		((Button)getDialogPane().lookupButton(ButtonType.CANCEL)).setText("_Cancel");

		showingProperty().addListener((obs, oldv, newv) -> {
			if (newv)
				fuzzySearchField.requestFocus();
		});

		this.sortedBookmarks.setAll(this.bookmarks);
		this.fuzzySearchField.setText("");

	}

	public Optional<BookmarkConfig.Bookmark> showAndWaitForBookmark() {
		if (ButtonType.OK.equals(showAndWait().orElse(null))) {
			return Optional.ofNullable(selectedBookmark.get());
		}
		return Optional.empty();
	}

	private void triggerOkWithBookmark(final BookmarkConfig.Bookmark bookmark) {
		this.selectedBookmark.set(bookmark);
		triggerOk();
	}

	private void triggerOk() {
		((Button)getDialogPane().lookupButton(ButtonType.OK)).fire();
	}

	private static class BookmarkWithFuzzyScore implements Comparable<BookmarkWithFuzzyScore> {

		private static final Applicable DEFAULT_SCORER = new WeightedRatio();

		private final BookmarkConfig.Bookmark bookmark;

		private final String fuzzyQuery;

		private final int score;

		private BookmarkWithFuzzyScore(
				final BookmarkConfig.Bookmark bookmark,
				final String fuzzyQuery) {
			this(bookmark, fuzzyQuery, DEFAULT_SCORER);
		}

		private BookmarkWithFuzzyScore(
				final BookmarkConfig.Bookmark bookmark,
				final String fuzzyQuery,
				final Applicable scorer) {
			this.bookmark = bookmark;
			this.fuzzyQuery = fuzzyQuery;
			this.score = scorer.apply(fuzzyQuery, bookmark.getNote() == null ? "" : bookmark.getNote());//.replace("\n", " "));
		}

		public BookmarkConfig.Bookmark getBookmark() {
			return this.bookmark;
		}

		@Override
		public int compareTo(@NotNull BookmarkWithFuzzyScore that) {
			return Integer.compare(this.score, that.score);
		}
	}
}
