package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Window
import kotlinx.coroutines.*
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState.Companion.ContainerLoaderCache
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.PainteraCache.appendLine
import org.janelia.saalfeldlab.util.PainteraCache.readLines
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class N5FactoryOpener(private val openSourceState: OpenSourceState) {


	private val selectionProperty: StringProperty = SimpleStringProperty()
	private var selection by selectionProperty.nullable()

	private val containerStateProperty: ObjectProperty<N5ContainerState?> = SimpleObjectProperty()
	private val isOpeningContainer: BooleanProperty = SimpleBooleanProperty(false)

	init {
		containerStateProperty.subscribe { it -> openSourceState.parseContainer(it) }
		selectionProperty.subscribe { _, new -> selectionChanged(new) }
		DEFAULT_DIRECTORY?.let {
			selection = Paths.get(it).toRealPath().toString()
		}
	}

	fun backendDialog(): OpenSourceBackend {
		var ownerWindow: Window? = null
		val containerField = stringField(selectionProperty.get(), SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
			valueProperty().bindBidirectional(selectionProperty)
			textField.apply {
				addEventFilter(KEY_PRESSED, createCachedContainerResetHandler())
				minWidth = 0.0
				maxWidth = Double.POSITIVE_INFINITY
				promptText = "N5 container"
				val tooltipBinding = Bindings.createObjectBinding({ Tooltip(text) }, textProperty())
				tooltipProperty().bind(tooltipBinding)

				textProperty().subscribe { _, new ->
					selectionChanged(new)
				}

				focusedProperty().subscribe { _, focus ->
					if (!focus)
						selectionChanged(text)
				}

				ownerWindow = this.scene?.window
			}
		}
		val onBrowseFoldersClicked = EventHandler<ActionEvent> {
			val startDir = selection?.let { path ->
				File(path).let { if (it.exists()) it else null }
			} ?: Path.of(".").toAbsolutePath().toFile()
			updateFromDirectoryChooser(startDir, ownerWindow)
		}

		val onBrowseFilesClicked = EventHandler<ActionEvent> {
			val startDir = selection?.let { path ->
				File(path).let {
					when {
						it.isDirectory -> it
						it.parentFile?.isDirectory == true -> it.parentFile
						else -> null
					}
				}
			} ?: Path.of(".").toAbsolutePath().toFile()
			updateFromFileChooser(startDir, ownerWindow)
		}

		val recentSelections = readLines(PainteraCache.RECENT_CACHE, "containers").reversed()
		val menuButton = BrowseRecentFavorites.menuButton(
			"_Find",
			recentSelections,
			FAVORITES,
			onBrowseFoldersClicked,
			onBrowseFilesClicked
		) { value: String -> selectionProperty.set(value) }

		val dialog = OpenSourceBackend(openSourceState, containerField.textField, menuButton, isOpeningContainer)
		dialog.visibleProperty.subscribe { visible -> if (visible) selectionChanged(selection) }
		return dialog
	}

	private fun createCachedContainerResetHandler() = EventHandler { event: KeyEvent ->
		if (event.code == KeyCode.ENTER) {
			val url = selectionProperty.get()
			n5ContainerStateCache.remove(url)?.let { ContainerLoaderCache.invalidate(it) }
			containerStateProperty.set(null)
			selectionChanged(url)
		}
	}

	fun selectionAccepted() {
		cacheCurrentSelectionAsRecent()
	}

	private fun cacheCurrentSelectionAsRecent() {
		val path = selectionProperty.get()
		if (path != null) appendLine(PainteraCache.RECENT_CACHE, "containers", path, 50)
	}

	private fun updateFromFileChooser(initialDirectory: File, owner: Window?) {
		FileChooser().also {
			it.extensionFilters.setAll(FileChooser.ExtensionFilter("h5", *H5_EXTENSIONS))
			it.initialDirectory = initialDirectory
		}.showOpenDialog(owner)?.let { updatedRoot ->
			LOG.debug { "Updating root to $updatedRoot (was $selection)" }
			if (updatedRoot.isFile)
				selection = updatedRoot.absolutePath
		}

	}

	private fun updateFromDirectoryChooser(initialDirectory: File, ownerWindow: Window?) {
		DirectoryChooser().also {
			it.initialDirectory = if (initialDirectory.isDirectory) initialDirectory else initialDirectory.parentFile
		}.showDialog(ownerWindow)?.let { updatedRoot ->
			LOG.debug { "Updating root to $updatedRoot (was $selection)" }
			n5Factory.openReaderOrNull(updatedRoot.absolutePath)?.let {
				selection = if (updatedRoot.absolutePath == selection) null else updatedRoot.absolutePath
			}
		}
	}

	private var createContainerStateJob: Job? = null

	private fun selectionChanged(newSelection: String?) {
		if (newSelection.isNullOrBlank()) {
			containerStateProperty.set(null)
			return
		}

		createContainerStateJob?.cancel("Cancelled by new selection")

		createContainerStateJob = CoroutineScope(Dispatchers.IO).launch {
			isOpeningContainer.set(true)

			n5ContainerStateCache.getOrPut(newSelection) {
				val state = n5Factory.openWriterOrReaderOrNull(newSelection)?.let { N5ContainerState(it) }
				ensureActive()
				state
			}?.let { containerStateProperty.set(it) }
				?: containerStateProperty.set(null)

		}.apply {
			invokeOnCompletion { cause ->
				when (cause) {
					null -> Unit
					is CancellationException -> LOG.trace(cause) {}
					else -> LOG.error(cause) { "Error opening container: $newSelection" }
				}
				isOpeningContainer.set(false)
			}
		}
	}

	companion object {
		private val DEFAULT_DIRECTORY = getPainteraConfig("data", "n5", "defaultDirectory") {
			getPainteraConfig<String?>("data", "defaultDirectory") { null }
		}

		private val FAVORITES: List<String> = getPainteraConfig("data", "n5", "favorites") { listOf() }

		private val H5_EXTENSIONS = arrayOf("*.h5", "*.hdf", "*.hdf5")

		private val LOG: KLogger = KotlinLogging.logger {}
		private val n5ContainerStateCache = HashMap<String, N5ContainerState?>()

		private fun <T> getPainteraConfig(vararg segments: String, fallback: () -> T) = PainteraConfigYaml.getConfig(fallback, *segments) as T
	}
}
