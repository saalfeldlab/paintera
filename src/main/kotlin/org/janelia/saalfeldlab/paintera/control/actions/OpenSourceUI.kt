package org.janelia.saalfeldlab.paintera.control.actions

import dev.dirs.UserDirectories
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.effect.InnerShadow
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraConfigYaml
import org.janelia.saalfeldlab.paintera.control.actions.OpenSourceModel.Companion.getDialog
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.CombinesErrorMessages
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.NameField
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.BrowseRecentFavorites
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.OpenSourceNode
import org.janelia.saalfeldlab.paintera.ui.hGrow
import org.janelia.saalfeldlab.util.PainteraCache
import org.janelia.saalfeldlab.util.PainteraCache.Companion.distinctCanonicalStrings
import org.janelia.saalfeldlab.util.PainteraCache.Companion.distinctCanonicalURIs
import java.io.File
import java.util.UUID
import java.util.function.Consumer

private val LOG = KotlinLogging.logger {}

class OpenSourceUI(val model: OpenSourceModel) : VBox(10.0), CombinesErrorMessages {

	private val typeProperty = model.typeProperty
	private val containerSelectionProperty = model.containerSelectionProperty
	var containerSelection: String? by containerSelectionProperty.nullable()

	val containerField = stringField(containerSelection, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
		valueProperty().bindBidirectional(containerSelectionProperty)
		with(textField) {
			minWidth = 0.0
			maxWidth = Double.POSITIVE_INFINITY
			prefColumnCount *= 2
			promptText = "Source location (N5/Zarr/HDF5)"
			tooltip = Tooltip().also { it.textProperty().bind(textProperty()) }
			textProperty().subscribe { _, new -> containerSelection = new }
			focusedProperty().subscribe { _, focus ->
				if (!focus)
					containerSelection = text
			}
		}
	}

	val browseButton: MenuButton = BrowseRecentFavorites.menuButton(
		"_Find",
		PainteraCache.RECENT_CONTAINERS.distinctCanonicalStrings(),
		FAVORITES,
		{ updateFromDirectoryChooser() },
		{ updateFromFileChooser() }
	) { containerSelection = it }.apply {
		minWidth = Region.USE_PREF_SIZE
	}

	val nameField = NameField("Source name", "Specify source name (required)", InnerShadow(10.0, Color.ORANGE)).apply {
		errorMessageProperty().subscribe { _, _ -> combineErrorMessages() }
		/* init sourceName to text, and listen for future programatic changes*/
		model.sourceNameProperty.subscribe { name -> textField().text = name }
		/* listen for text input and set sourceName*/
		textField().textProperty().subscribe { _, text -> model.sourceName = text ?: "" }
	}

	val openSourceNode = OpenSourceNode(model)

	val metaNode = OpenSourceMetaNode(model)

	private val errorMessage = Label("")
	val hasErrorProperty = errorMessage.textProperty().createObservableBinding { it.get()?.isNotEmpty() == true }
	private val errorInfo = TitledPane("", errorMessage).apply {
		visibleProperty().bind(hasErrorProperty)
		text = "ERROR"
	}

	init {
		padding = Insets(10.0)

		model.metadataStateBinding.subscribe { it ->
			val sourceType = when {
				it == null -> return@subscribe
				it.isLabel -> SourceType.LABEL
				else -> SourceType.RAW
			}
			InvokeOnJavaFXApplicationThread { model.type = sourceType }
		}

		with(model) {
			val openSourceStateChangeBinding = activeNodeProperty.createObservableBinding(containerStateProperty) { UUID.randomUUID() }
			openSourceStateChangeBinding.subscribe { _ ->
				openSourceStateChangeBinding.get()
				combineErrorMessages()
			}
		}

		val typeChoiceButton = MatchSelectionMenuButton(SourceType.entries.map(SourceType::toString), "_Type") {
			model.type = SourceType.valueOf(it!!)
		}.apply {
			minWidth = Region.USE_PREF_SIZE
			textProperty().bind(typeProperty.createObservableBinding { "_Type${it.get().let { ": $it" }}" })
			tooltip = Tooltip().also { it.textProperty().bind(typeProperty.createObservableBinding { "Type of the dataset: $it" }) }
		}

		val sourceSelectionRow = HBox(10.0,
			typeChoiceButton,
			VBox(5.0,
				HBox(5.0, containerField.textField.hGrow(), browseButton),
				openSourceNode
			).hGrow()
		)

		val statusBar = TextField().apply {
			background = Background(BackgroundFill(Color.TRANSPARENT, null, null))
			isEditable = false
			textProperty().bind(model.statusProperty)
		}

		children += nameField.textField()
		children += sourceSelectionRow
		children += statusBar
		children += metaNode
		children += errorInfo
	}

	private fun updateFromFile(selection: File?) {
		val file = selection ?: return
		val newSelection = file.absolutePath
		val curSelection = containerSelection
		LOG.trace { "Updating container to $newSelection (was $curSelection)" }
		containerSelection = newSelection
	}

	private fun updateFromDirectoryChooser() = DirectoryChooser().run {
		initialDirectory = startDirFromSelection()
		updateFromFile(showDialog(scene?.window))
	}

	private fun updateFromFileChooser() = FileChooser().run {
		initialDirectory = startDirFromSelection()
		extensionFilters.setAll(FileChooser.ExtensionFilter("h5", *H5_EXTENSIONS))
		updateFromFile(showOpenDialog(scene?.window))
	}

	override fun errorMessages(): Collection<ObservableValue<String>> {
		val errors = mutableListOf<ObservableValue<String>>()
		errors += nameField.errorMessageProperty()
		with(model) {
			containerState ?: let { errors += SimpleStringProperty("No Valid Container") }
			activeNode ?: let { errors += SimpleStringProperty("No Dataset Selected") }
		}
		return errors
	}

	override fun combiner(): Consumer<Collection<String>> {
		return Consumer { strings ->
			val error = strings.joinToString("\n")
			InvokeOnJavaFXApplicationThread { errorMessage.text = error }
		}
	}

	private fun startDirFromSelection() = containerSelection
		?.let { path -> File(path).takeIf { it.exists() } }
		?: File(UserDirectories.get().homeDir)

	companion object {
		internal val FAVORITES: List<String> = getPainteraConfig("data", "n5", "favorites") { listOf() }
		internal val H5_EXTENSIONS = arrayOf("*.h5", "*.hdf", "*.hdf5")

		@Suppress("UNCHECKED_CAST")
		private fun <T> getPainteraConfig(vararg segments: String, fallback: () -> T) =
			PainteraConfigYaml.getConfig(fallback, *segments) as T
	}
}

fun main() {
	InvokeOnJavaFXApplicationThread {
		OpenSourceModel.default().getDialog().showAndWait()
	}
}
