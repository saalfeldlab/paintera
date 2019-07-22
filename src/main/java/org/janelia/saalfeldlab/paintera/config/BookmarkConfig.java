package org.janelia.saalfeldlab.paintera.config;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.transform.Affine;
import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class BookmarkConfig {

	private static Duration DEFAULT_TRANSITION_TIME = Duration.millis(300);

	public static class Bookmark {

		private final AffineTransform3D globalTransform;

		private final Affine viewer3DTransform;

		private String note;

		private final List<Consumer<Bookmark>> applyBookmarkListeners = new ArrayList<>();

		public Bookmark(
				final AffineTransform3D globalTransform,
				final Affine viewer3DTransform,
				final String note) {
			this.globalTransform = globalTransform;
			this.viewer3DTransform = viewer3DTransform;
			this.note = note;
		}

		public AffineTransform3D getGlobalTransformCopy() {
			return globalTransform.copy();
		}

		public Affine getViewer3DTransformCopy() {
			return viewer3DTransform.clone();
		}

		public String getNote() {
			return note;
		}

		public Bookmark withNote(final String note) {
			return new Bookmark(
					getGlobalTransformCopy(),
					getViewer3DTransformCopy(),
					note);
		}
	}

	private final ObservableList<Bookmark> bookmarks = FXCollections.observableArrayList();

	private final ObservableList<Bookmark> unmodifiableBookmarks = FXCollections.unmodifiableObservableList(bookmarks);

	private final ObjectProperty<Duration> transitionTime = new SimpleObjectProperty<>(this, "transitionTime", DEFAULT_TRANSITION_TIME);

	public void addBookmark(final Bookmark bookmark) {
		this.bookmarks.add(bookmark);
	}

	public void addBookmarks(final Bookmark... bookmark) {
		this.bookmarks.addAll(bookmarks);
	}

	public void removeBookmark(final Bookmark bookmark) {
		this.bookmarks.remove(bookmark);
	}

	public void removeBookmarks(final Bookmark... bookmark) {
		this.bookmarks.removeAll(bookmark);
	}

	public void setAll(final Collection<? extends Bookmark> bookmarks) {
		this.bookmarks.setAll(bookmarks);
	}

	public void replaceBookmark(final Bookmark replaced, final Bookmark with) {
		final int listIndex = bookmarks.indexOf(replaced);
		if (listIndex >= 0)
			this.bookmarks.set(listIndex, with);
		else
			addBookmark(with);

	}

	public ObservableList<Bookmark> getUnmodifiableBookmarks() {
		return this.unmodifiableBookmarks;
	}

	public ObjectProperty<Duration> transitionTimeProperty() {
		return this.transitionTime;
	}

	public Duration getTransitionTime() {
		return transitionTimeProperty().get();
	}

	public void setTransitionTime(final Duration duration) {
		transitionTimeProperty().set(duration);
	}

}
