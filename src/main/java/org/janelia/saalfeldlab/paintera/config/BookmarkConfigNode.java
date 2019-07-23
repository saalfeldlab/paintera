package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Affine;
import javafx.util.Duration;
import javafx.util.Pair;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class BookmarkConfigNode extends TitledPane {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class BookmarkNode extends VBox {

		private final TextArea label;

		private BookmarkNode(final BookmarkConfig.Bookmark bookmark) {
			label = new TextArea(bookmark.getNote());
			label.setEditable(false);
			label.setWrapText(false);
			label.setTooltip(new Tooltip(label.getText()));

			final Node globalTransformGrid = affineTransformGrid(bookmark.getGlobalTransformCopy());
			final TitledPane globalTransformPane = TitledPanes.createCollapsed("Global Transform", globalTransformGrid);
			globalTransformPane.setPadding(Insets.EMPTY);


			final Node viewer3DTransformGrid = viewer3DTransformGrid(bookmark.getViewer3DTransformCopy());
			final TitledPane viewer3DTransformPane = TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid);
			viewer3DTransformPane.setPadding(Insets.EMPTY);

			getChildren().addAll(label, globalTransformPane, viewer3DTransformPane);
		}

	}

	private static class BookmarkTitledPane extends TitledPane {
		private BookmarkTitledPane(
				final BookmarkConfig.Bookmark bookmark,
				final int id,
				final BiConsumer<BookmarkConfig.Bookmark, BookmarkConfig.Bookmark> replace,
				final Consumer<BookmarkConfig.Bookmark> onApply,
				final Consumer<BookmarkConfig.Bookmark> onRemove) {
			super(null, null);
			final int oneBasedId = id + 1;
			setText("" + oneBasedId);
			final Button goThere = new Button("Apply");
			final Button closeIt = new Button("x");
			final Button updateNote = new Button("Update Note");

			goThere.setOnAction(e -> onApply.accept(bookmark));
			closeIt.setOnAction(e -> {
				final Alert dialog = bookmarkDialog(bookmark).getKey();
				dialog.setHeaderText("Remove bookmark " + oneBasedId + "?");
				if (ButtonType.OK.equals(dialog.showAndWait().orElse(ButtonType.CANCEL)))
					onRemove.accept(bookmark);
			});
			updateNote.setOnAction(e -> {
				final Pair<Alert, TextArea> dialog = bookmarkDialog(bookmark);
				dialog.getKey().setHeaderText("Update Bookmark Note");
				if (ButtonType.OK.equals(dialog.getKey().showAndWait().orElse(ButtonType.CANCEL)))
					replace.accept(bookmark, bookmark.withNote(dialog.getValue().getText()));
			});

			closeIt.setTooltip(new Tooltip("Remove bookmark " + oneBasedId));

			final Label noteLabel = Labels.withTooltip(Optional.ofNullable(bookmark.getNote()).map(n -> n.replace("\n", " ")).orElse(null));
			noteLabel.setPrefWidth(100.0);
			final HBox hBox = new HBox(noteLabel, goThere, updateNote, closeIt);
			hBox.setAlignment(Pos.CENTER);

			setGraphic(hBox);
			setPadding(Insets.EMPTY);
			setExpanded(false);
			setContent(new BookmarkNode(bookmark));
			setContentDisplay(ContentDisplay.RIGHT);
		}

	}

	private static TextField matrixField(final String text) {
		final TextField tf = new TextField(text);
		tf.setMinWidth(30.0);
		tf.setEditable(false);
		return tf;
	}

	private static Node affineTransformGrid(final AffineTransform3D transform) {
		final GridPane transformGrid = new GridPane();
		for (int i = 0; i < 3; ++i)
			for (int k = 0; k < 4; ++k)
				transformGrid.add(matrixField(Double.toString(transform.get(i, k))), k, i);
		transformGrid.setPadding(Insets.EMPTY);
		return transformGrid;
	}

	private static Node viewer3DTransformGrid(final Affine transform) {
		final GridPane transformGrid = new GridPane();
		transformGrid.add(matrixField(Double.toString(transform.getMxx())),0, 0);
		transformGrid.add(matrixField(Double.toString(transform.getMxy())),1, 0);
		transformGrid.add(matrixField(Double.toString(transform.getMxz())),2, 0);
		transformGrid.add(matrixField(Double.toString(transform.getMyx())),0, 1);
		transformGrid.add(matrixField(Double.toString(transform.getMyy())),1, 1);
		transformGrid.add(matrixField(Double.toString(transform.getMyz())),2, 1);
		transformGrid.add(matrixField(Double.toString(transform.getMzx())),0, 2);
		transformGrid.add(matrixField(Double.toString(transform.getMzy())),1, 2);
		transformGrid.add(matrixField(Double.toString(transform.getMzz())),2, 2);

		transformGrid.add(matrixField(Double.toString(transform.getTx())),3, 0);
		transformGrid.add(matrixField(Double.toString(transform.getTx())),3, 1);
		transformGrid.add(matrixField(Double.toString(transform.getTx())),3, 2);

		transformGrid.setPadding(Insets.EMPTY);
		return transformGrid;
	}

	private final Consumer<BookmarkConfig.Bookmark> applyBookmark;

	private final ObjectProperty<BookmarkConfig> bookmarkConfig = new SimpleObjectProperty<>();

	private final ObjectProperty<Duration> transitionTime = new SimpleObjectProperty<>(Duration.millis(300.0));

	private final NumericSliderWithField transitionTimeSlider = new NumericSliderWithField(0.0, 1000.0, 300.0);

	private final VBox bookmarkSettings = new VBox(new HBox(new Label("Transition Time"), transitionTimeSlider.slider(), transitionTimeSlider.textField()));

	private final VBox bookmarkNodes = new VBox();

	private final ChangeListener<BookmarkConfig> configListener = (obs, oldv, newv) -> {
		if (oldv != null) {
			oldv.getUnmodifiableBookmarks().removeListener(this.listListener);
			this.transitionTime.unbindBidirectional(oldv.transitionTimeProperty());
		}
		if (newv !=null) {
			newv.getUnmodifiableBookmarks().addListener(this.listListener);
			this.transitionTime.bindBidirectional(newv.transitionTimeProperty());
			this.transitionTime.set(newv.transitionTimeProperty().get());
			updateChildren(new ArrayList<>(newv.getUnmodifiableBookmarks()), newv::replaceBookmark, newv::removeBookmark);
		}
	};

	private final ListChangeListener<BookmarkConfig.Bookmark> listListener = change -> updateChildren(
			change.getList(),
			bookmarkConfig.get()::replaceBookmark,
			bookmarkConfig.get()::removeBookmark);

	public BookmarkConfigNode(final Consumer<BookmarkConfig.Bookmark> applyBookmark) {
		super("Bookmarks", null);
		this.applyBookmark = applyBookmark;

		this.transitionTimeSlider.slider().valueProperty().addListener((obs, oldv, newv) -> this.transitionTime.set(Duration.millis(newv.doubleValue())));
		this.transitionTime.addListener((obs, oldv, newv) -> this.transitionTimeSlider.slider().setValue(newv.toMillis()));

		setExpanded(false);
		this.bookmarkConfig.addListener(configListener);
		bookmarkSettings.setPadding(Insets.EMPTY);
		bookmarkNodes.setPadding(Insets.EMPTY);
		setContent(new VBox(bookmarkSettings, bookmarkNodes));
	}

	private void updateChildren(
			final List<? extends BookmarkConfig.Bookmark> bookmarks,
			final BiConsumer<BookmarkConfig.Bookmark, BookmarkConfig.Bookmark> replaceBookmark,
			final Consumer<BookmarkConfig.Bookmark> removeBookmark) {
		LOG.debug("Updating contents with {}", bookmarks);
		final Node[] nodes = IntStream
				.range(0, bookmarks.size())
				.mapToObj(i -> new BookmarkTitledPane(bookmarks.get(i), i, replaceBookmark, applyBookmark, removeBookmark))
				.toArray(Node[]::new);
		bookmarkNodes.getChildren().setAll(nodes);
	}

	public ObjectProperty<BookmarkConfig> bookmarkConfigProperty() {
		return this.bookmarkConfig;
	}

	public void setBookmarkConfig(final BookmarkConfig bookmarkConfig) {
		bookmarkConfigProperty().set(bookmarkConfig);
	}

	public BookmarkConfig getBookmarkConfig() {
		return bookmarkConfigProperty().get();
	}

	public void requestAddNewBookmark(
			final AffineTransform3D globalTransform,
			final Affine viewer3DTransform) {

		final Pair<Alert, TextArea> dialog = bookmarkDialog(globalTransform, viewer3DTransform, null);
		dialog.getKey().setHeaderText("Bookmark current view");

		final Optional<ButtonType> bt = dialog.getKey().showAndWait();

		if (ButtonType.OK.equals(bt.orElse(ButtonType.CANCEL))) {
			bookmarkConfig.get().addBookmark(new BookmarkConfig.Bookmark(
					globalTransform,
					viewer3DTransform,
					dialog.getValue().getText()));
		}

	}

	private static Pair<Alert, TextArea> bookmarkDialog(
			final BookmarkConfig.Bookmark bookmark) {
		return bookmarkDialog(bookmark.getGlobalTransformCopy(), bookmark.getViewer3DTransformCopy(), bookmark.getNote());
	}

	private static Pair<Alert, TextArea> bookmarkDialog(
			final AffineTransform3D globalTransform,
			final Affine viewer3DTransform,
			final String note) {
		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		final TextArea label = new TextArea(note);

		dialog.getDialogPane().setContent(new VBox(
				label,
				TitledPanes.createCollapsed("Global Transform", affineTransformGrid(globalTransform)),
				TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid(viewer3DTransform))));
		return new Pair<>(dialog, label);

	}

}
