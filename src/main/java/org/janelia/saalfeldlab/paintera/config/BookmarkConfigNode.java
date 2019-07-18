package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.InvalidationListener;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Affine;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class BookmarkConfigNode extends TitledPane {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static class BookmarkNode extends GridPane {

		private static TextField noneditableTextField(final String text) {
			final TextField tf = new TextField(text);
			tf.setEditable(false);
			return tf;
		}

		private BookmarkNode(final BookmarkConfig.Bookmark bookmark) {
			final TextField label = noneditableTextField(bookmark.getNote());
			label.setTooltip(new Tooltip(label.getText()));
			label.setMaxWidth(Double.POSITIVE_INFINITY);

			final AffineTransform3D globalTransform = bookmark.getGlobalTransformCopy();
			final GridPane globalTransformGrid = new GridPane();
			for (int i = 0; i < 3; ++i)
				for (int k = 0; k < 4; ++k)
					globalTransformGrid.add(noneditableTextField(Double.toString(globalTransform.get(i, k))), k, i);
			final TitledPane globalTransformPane = TitledPanes.createCollapsed("Global Transform", globalTransformGrid);


			final Affine viewer3DTransform = bookmark.getViewer3DTransformCopy();
			final GridPane viewer3DTransformGrid = new GridPane();
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMxx())),0, 0);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMxy())),1, 0);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMxz())),2, 0);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMyx())),0, 1);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMyy())),1, 1);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMyz())),2, 1);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMzx())),0, 2);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMzy())),1, 2);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getMzz())),2, 2);

			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getTx())),3, 0);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getTx())),3, 1);
			viewer3DTransformGrid.add(noneditableTextField(Double.toString(viewer3DTransform.getTx())),3, 2);

			final TitledPane viewer3DTransformPane = TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid);

			add(label, 0, 0);
			add(globalTransformPane, 1, 0);
			add(viewer3DTransformPane, 1, 1);

			GridPane.setHgrow(label, Priority.ALWAYS);
			GridPane.setHgrow(globalTransformPane, Priority.NEVER);
		}

	}

	private final BookmarkConfig bookmarkConfig;

	public BookmarkConfigNode(final BookmarkConfig bookmarkConfig) {
		super("Bookmarks", null);
		setExpanded(false);
		this.bookmarkConfig = bookmarkConfig;
		this.bookmarkConfig.getUnmodifiableBookmarks().addListener((InvalidationListener) obs -> updateChildren());
		updateChildren();
	}

	private void updateChildren() {
		LOG.debug("Updating contents with {}", bookmarkConfig.getUnmodifiableBookmarks());
		final BookmarkNode[] nodes = bookmarkConfig
				.getUnmodifiableBookmarks()
				.stream()
				.map(BookmarkNode::new)
				.toArray(BookmarkNode[]::new);
		setContent(new VBox(nodes));
	}

}
