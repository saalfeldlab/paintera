package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Affine;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Optional;

public class BookmarkConfigNode extends TitledPane {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class BookmarkNode extends HBox {

		private BookmarkNode(final BookmarkConfig.Bookmark bookmark) {
			final TextArea label = new TextArea(bookmark.getNote());
			label.setEditable(false);
			label.setWrapText(false);
			label.setTooltip(new Tooltip(label.getText()));

			final Node globalTransformGrid = affineTransformGrid(bookmark.getGlobalTransformCopy());
			final TitledPane globalTransformPane = TitledPanes.createCollapsed("Global Transform", globalTransformGrid);
			globalTransformPane.setPadding(Insets.EMPTY);


			final Node viewer3DTransformGrid = viewer3DTransformGrid(bookmark.getViewer3DTransformCopy());
			final TitledPane viewer3DTransformPane = TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid);
			viewer3DTransformPane.setPadding(Insets.EMPTY);

			getChildren().addAll(label, new VBox(globalTransformPane, viewer3DTransformPane));
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
		final TitledPane globalTransformPane = TitledPanes.createCollapsed("Global Transform", transformGrid);
		globalTransformPane.setPadding(Insets.EMPTY);
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

		final TitledPane viewer3DTransformPane = TitledPanes.createCollapsed("3D Viewer Transform", transformGrid);
		viewer3DTransformPane.setPadding(Insets.EMPTY);
		transformGrid.setPadding(Insets.EMPTY);
		return viewer3DTransformPane;
	}

	private final ObjectProperty<BookmarkConfig> bookmarkConfig = new SimpleObjectProperty<>();

	private final ChangeListener<BookmarkConfig> configListener = (obs, oldv, newv) -> {
		if (oldv != null)
			oldv.getUnmodifiableBookmarks().removeListener(this.listListener);
		if (newv !=null) {
			newv.getUnmodifiableBookmarks().addListener(this.listListener);
			updateChildren(newv.getUnmodifiableBookmarks());
		}
	};

	private final ListChangeListener<BookmarkConfig.Bookmark> listListener = change -> {
		updateChildren(change.getList());
	};

	public BookmarkConfigNode() {
		super("Bookmarks", null);
		setExpanded(false);
		this.bookmarkConfig.addListener(configListener);
	}

	private void updateChildren(final Collection<? extends BookmarkConfig.Bookmark> bookmarks) {
		LOG.debug("Updating contents with {}", bookmarks);
		final BookmarkNode[] nodes = bookmarks
				.stream()
				.map(BookmarkNode::new)
				.toArray(BookmarkNode[]::new);
		setContent(new VBox(nodes));
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

		final Alert dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true);
		dialog.setHeaderText("Bookmark current view");
		final TextArea label = new TextArea(null);

		dialog.getDialogPane().setContent(new VBox(
				label,
				TitledPanes.createCollapsed("Global Transform", affineTransformGrid(globalTransform)),
				TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid(viewer3DTransform))));

		final Optional<ButtonType> bt = dialog.showAndWait();

		if (ButtonType.OK.equals(bt.orElse(ButtonType.CANCEL))) {
			bookmarkConfig.get().addBookmark(new BookmarkConfig.Bookmark(
					globalTransform,
					viewer3DTransform,
					label.getText()));
		}

	}

}
