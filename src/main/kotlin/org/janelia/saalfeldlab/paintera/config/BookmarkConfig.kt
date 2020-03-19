package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.scene.transform.Affine
import javafx.util.Duration
import net.imglib2.realtransform.AffineTransform3D
import java.util.function.Consumer

class BookmarkConfig {

    private val bookmarks = FXCollections.observableArrayList<Bookmark>()

    val unmodifiableBookmarks = FXCollections.unmodifiableObservableList(bookmarks)!!

    private val transitionTime = SimpleObjectProperty(this, "transitionTime", DEFAULT_TRANSITION_TIME)

    class Bookmark(
            private val globalTransform: AffineTransform3D,
            private val viewer3DTransform: Affine,
            val note: String?) {

        private val applyBookmarkListeners = mutableListOf<Consumer<Bookmark>>()

        val globalTransformCopy: AffineTransform3D
            get() = globalTransform.copy()

        val viewer3DTransformCopy: Affine
            get() = viewer3DTransform.clone()

        fun withNote(note: String) = Bookmark(globalTransformCopy, viewer3DTransformCopy, note)
    }

    fun addBookmark(bookmark: Bookmark) = this.bookmarks.add(bookmark)

    fun addBookmarks(vararg bookmark: Bookmark) = this.bookmarks.addAll(bookmarks)

    fun removeBookmark(bookmark: Bookmark) = this.bookmarks.remove(bookmark)

    fun removeBookmarks(vararg bookmark: Bookmark) = this.bookmarks.removeAll(*bookmark)

    fun setAll(bookmarks: Collection<out Bookmark>) = this.bookmarks.setAll(bookmarks)

    fun replaceBookmark(replaced: Bookmark, with: Bookmark) {
        val listIndex = bookmarks.indexOf(replaced)
        if (listIndex >= 0)
            this.bookmarks[listIndex] = with
        else
            addBookmark(with)

    }

    fun transitionTimeProperty(): ObjectProperty<Duration> = this.transitionTime

    fun getTransitionTime() = transitionTimeProperty().get()

    fun setTransitionTime(duration: Duration) = transitionTimeProperty().set(duration)

    companion object {
        private val DEFAULT_TRANSITION_TIME = Duration.millis(300.0)
    }

}
