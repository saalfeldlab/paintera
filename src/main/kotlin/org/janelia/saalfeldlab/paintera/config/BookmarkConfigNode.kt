package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.transform.Affine
import javafx.util.Duration
import javafx.util.Pair
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.MarkdownPane
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.Optional
import java.util.function.BiConsumer
import java.util.function.Consumer

class BookmarkConfigNode private constructor(private val applyBookmark: (BookmarkConfig.Bookmark) -> Unit) : TitledPane("Bookmarks", null) {

	constructor(bookmarkConfig: BookmarkConfig, applyBookmark: (BookmarkConfig.Bookmark) -> Unit) : this(applyBookmark) {
		Paintera.whenPaintable {
			InvokeOnJavaFXApplicationThread {
				this@BookmarkConfigNode.bookmarkConfig.set(bookmarkConfig)
			}
		}
	}

	// TODO change this into a regular bookmark
	private val bookmarkConfig = SimpleObjectProperty<BookmarkConfig>()

	private val transitionTime = SimpleObjectProperty(Duration.millis(300.0))

	private val transitionTimeSlider = NumericSliderWithField(0.0, 1000.0, 300.0)

	private val bookmarkSettings = VBox(HBox(Label("Transition Time"), transitionTimeSlider.slider, transitionTimeSlider.textField))

	private val bookmarkNodes = VBox()

	private val configListener = ChangeListener<BookmarkConfig> { _, oldv, newv ->
		oldv?.let {
			it.unmodifiableBookmarks.removeListener(this.listListener)
			this.transitionTime.unbindBidirectional(it.transitionTimeProperty())
		}
		newv?.let {
			it.unmodifiableBookmarks.addListener(this.listListener)
			this.transitionTime.bindBidirectional(it.transitionTimeProperty())
			this.transitionTime.set(it.transitionTimeProperty().get())
			updateChildren(
				it.unmodifiableBookmarks,
				{ replaced, with -> it.replaceBookmark(replaced, with) },
				{ bm -> it.removeBookmark(bm) })
		}
	}

	private val listListener = ListChangeListener<BookmarkConfig.Bookmark> {
		updateChildren(
			it.getList(),
			{ replaced, with -> bookmarkConfig.get().replaceBookmark(replaced, with) },
			{ bm -> bookmarkConfig.get().removeBookmark(bm) })
	}

	private class BookmarkNode constructor(bookmark: BookmarkConfig.Bookmark) : VBox() {

		private val markdownNode = MarkdownPane().also { it.text = bookmark.note }

		init {
			markdownNode.isEditable = false
			markdownNode.isWrapText = false
			markdownNode.tooltip = Tooltip(markdownNode.text)
			markdownNode.showRenderedTab()

			val globalTransformGrid = affineTransformGrid(bookmark.globalTransformCopy)
			val globalTransformPane = TitledPanes.createCollapsed("Global Transform", globalTransformGrid)
			globalTransformPane.padding = Insets.EMPTY


			val viewer3DTransformGrid = viewer3DTransformGrid(bookmark.viewer3DTransformCopy)
			val viewer3DTransformPane = TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid)
			viewer3DTransformPane.padding = Insets.EMPTY

			children.addAll(markdownNode, globalTransformPane, viewer3DTransformPane)
		}

	}

	private class BookmarkTitledPane constructor(
		bookmark: BookmarkConfig.Bookmark,
		id: Int,
		replace: BiConsumer<BookmarkConfig.Bookmark, BookmarkConfig.Bookmark>,
		onApply: Consumer<BookmarkConfig.Bookmark>,
		onRemove: Consumer<BookmarkConfig.Bookmark>
	) : TitledPane(null, null) {
		init {
			val oneBasedId = id + 1
			text = "" + oneBasedId
			val goThere = Button("Apply")
			val closeIt = Button("x")
			val updateNote = Button("Update Note")

			goThere.setOnAction { onApply.accept(bookmark) }
			closeIt.setOnAction {
				val dialogAndMarkdown = bookmarkDialog(bookmark)
				val dialog = dialogAndMarkdown.key
				dialogAndMarkdown.value.isEditable = false
				dialogAndMarkdown.value.showRenderedTab()
				dialog.headerText = "Remove bookmark $oneBasedId?"
				if (ButtonType.OK == dialog.showAndWait().orElse(ButtonType.CANCEL))
					onRemove.accept(bookmark)
			}
			updateNote.setOnAction {
				val dialog = bookmarkDialog(bookmark)
				dialog.key.headerText = "Update Bookmark Note"
				if (ButtonType.OK == dialog.key.showAndWait().orElse(ButtonType.CANCEL))
					replace.accept(bookmark, bookmark.withNote(dialog.value.text ?: ""))
			}

			closeIt.tooltip = Tooltip("Remove bookmark $oneBasedId")

			val noteLabel = Labels.withTooltip(Optional.ofNullable(bookmark.note).map { n -> n.replace("\n", " ") }.orElse(""))
			noteLabel.prefWidth = 100.0
			val hBox = HBox(noteLabel, goThere, updateNote, closeIt)
			hBox.alignment = Pos.CENTER

			graphic = hBox
			padding = Insets.EMPTY
			isExpanded = false
			content = BookmarkNode(bookmark)
			contentDisplay = ContentDisplay.RIGHT
		}

	}

	init {

		this.transitionTimeSlider.slider.valueProperty().addListener { _, _, newv -> this.transitionTime.set(Duration.millis(newv.toDouble())) }
		this.transitionTime.addListener { _, _, newv -> this.transitionTimeSlider.slider.value = newv.toMillis() }

		isExpanded = false
		this.bookmarkConfig.addListener(configListener)
		bookmarkSettings.padding = Insets.EMPTY
		bookmarkNodes.padding = Insets.EMPTY
		content = VBox(bookmarkSettings, bookmarkNodes)
	}

	private fun updateChildren(
		bookmarks: List<BookmarkConfig.Bookmark>,
		replaceBookmark: BiConsumer<BookmarkConfig.Bookmark, BookmarkConfig.Bookmark>,
		removeBookmark: Consumer<BookmarkConfig.Bookmark>
	) {
		LOG.debug("Updating contents with {}", bookmarks)
		val nodes = Array(bookmarks.size) { BookmarkTitledPane(bookmarks[it], it, replaceBookmark, applyBookmark, removeBookmark) }
		bookmarkNodes.children.setAll(*nodes)
	}

	fun requestAddNewBookmark(
		globalTransform: AffineTransform3D,
		viewer3DTransform: Affine
	) {

		val dialog = bookmarkDialog(globalTransform, viewer3DTransform, null)
		dialog.key.headerText = "Bookmark current view"

		val bt = dialog.key.showAndWait()

		if (ButtonType.OK == bt.orElse(ButtonType.CANCEL)) {
			bookmarkConfig.get().addBookmark(
				BookmarkConfig.Bookmark(
					globalTransform,
					viewer3DTransform,
					dialog.value.text
				)
			)
		}

	}

	companion object {

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private fun matrixField(text: String): TextField {
			val tf = TextField(text)
			tf.minWidth = 30.0
			tf.isEditable = false
			return tf
		}

		private fun affineTransformGrid(transform: AffineTransform3D): Node {
			val transformGrid = GridPane()
			for (i in 0..2)
				for (k in 0..3)
					transformGrid.add(matrixField(java.lang.Double.toString(transform.get(i, k))), k, i)
			transformGrid.padding = Insets.EMPTY
			return transformGrid
		}

		private fun viewer3DTransformGrid(transform: Affine): Node {
			val transformGrid = GridPane()
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mxx)), 0, 0)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mxy)), 1, 0)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mxz)), 2, 0)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.myx)), 0, 1)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.myy)), 1, 1)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.myz)), 2, 1)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mzx)), 0, 2)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mzy)), 1, 2)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.mzz)), 2, 2)

			transformGrid.add(matrixField(java.lang.Double.toString(transform.tx)), 3, 0)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.tx)), 3, 1)
			transformGrid.add(matrixField(java.lang.Double.toString(transform.tx)), 3, 2)

			transformGrid.padding = Insets.EMPTY
			return transformGrid
		}

		private fun bookmarkDialog(
			bookmark: BookmarkConfig.Bookmark
		): Pair<Alert, MarkdownPane> {
			return bookmarkDialog(bookmark.globalTransformCopy, bookmark.viewer3DTransformCopy, bookmark.note)
		}

		private fun bookmarkDialog(
			globalTransform: AffineTransform3D,
			viewer3DTransform: Affine,
			note: String?
		): Pair<Alert, MarkdownPane> {
			val dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
			val editor = MarkdownPane()
				.also { it.text = note }
				.also { it.showEditTab() }

			dialog.dialogPane.content = VBox(
				editor,
				TitledPanes.createCollapsed("Global Transform", affineTransformGrid(globalTransform)),
				TitledPanes.createCollapsed("3D Viewer Transform", viewer3DTransformGrid(viewer3DTransform))
			)
			return Pair(dialog, editor)

		}
	}

}
