package org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5

import dev.dirs.UserDirectories
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.effect.InnerShadow
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.GridPane
import javafx.scene.layout.GridPane.REMAINING
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.util.Callback
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.MatchSelectionMenuButton
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.CombinesErrorMessages
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.NameField
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.OpenSourceState
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.OpenDialogMenuEntry
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.menu.n5.N5FactoryOpener.Companion.H5_EXTENSIONS
import org.janelia.saalfeldlab.paintera.ui.dialogs.open.meta.MetaPanel
import org.janelia.saalfeldlab.paintera.ui.menus.PainteraMenuItems
import org.janelia.saalfeldlab.util.PainteraCache
import java.io.File
import java.util.UUID
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

class OpenSourceDialog(
	val state: OpenSourceState,
	val containerSelectionProperty: StringProperty
) : Dialog<OpenSourceState>(), CombinesErrorMessages {

	val typeProperty = SimpleObjectProperty<MetaPanel.TYPE>(MetaPanel.TYPE.entries[0])
	var type: MetaPanel.TYPE by typeProperty.nonnull()

	var containerSelection: String? by containerSelectionProperty.nullable()

	val containerField = stringField(containerSelection, SubmitOn.ENTER_PRESSED, SubmitOn.FOCUS_LOST).apply {
		valueProperty().bindBidirectional(containerSelectionProperty)
		with(textField) {
			minWidth = 0.0
			maxWidth = Double.POSITIVE_INFINITY
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
		PainteraCache.RECENT_CONTAINERS.readLines().reversed(),
		N5FactoryOpener.FAVORITES,
		EventHandler { updateFromDirectoryChooser() },
		EventHandler { updateFromFileChooser() }
	) { containerSelection = it }

	val isBusy = SimpleBooleanProperty(false)

	val nameField = NameField("Source name", "Specify source name (required)", InnerShadow(10.0, Color.ORANGE)).apply {
		errorMessageProperty().subscribe { _, _ -> combineErrorMessages() }
		state.sourceNameProperty.subscribe { name -> textField().text = name }
	}

	val openSourceNode = OpenSourceNode(state, containerField.textField, browseButton, isBusy)

	val metaPanel = MetaPanel(state)

	private val errorMessage = Label("")
	private val hasErrorProperty = errorMessage.textProperty().createObservableBinding { it.get()?.isNotEmpty() == true }
	private val errorInfo = TitledPane("", errorMessage).apply {
		visibleProperty().bind(hasErrorProperty)
		text = "ERROR"
	}


	init {

		Paintera.registerStylesheets(dialogPane)

		resultConverter = Callback { if (it == ButtonType.OK) state else null }
		state.metadataStateBinding.subscribe { it ->
			type = when {
				it == null -> return@subscribe
				it.isLabel -> MetaPanel.TYPE.LABEL
				else -> MetaPanel.TYPE.RAW
			}
		}



		title = Constants.NAME
		dialogPane.buttonTypes += arrayOf(ButtonType.CANCEL, ButtonType.OK)
		(dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
		(dialogPane.lookupButton(ButtonType.OK) as Button).apply {
			text = "_OK"
			disableProperty().bind(hasErrorProperty)
		}

		with(state) {
			var openSourceStateChangeBinding = activeNodeProperty.createObservableBinding(containerStateProperty) { UUID.randomUUID() }
			openSourceStateChangeBinding.subscribe { _ ->
				openSourceStateChangeBinding.get() //Necessary to re-validate the value (internal, and annoying)
				combineErrorMessages()
			}
		}
		val grid = GridPane()
		/* add choice button */
		val typeChoiceButton = MatchSelectionMenuButton(MetaPanel.TYPE.entries.map(MetaPanel.TYPE::toString), "_Type") {
			type = MetaPanel.TYPE.valueOf(it!!)
		}.apply {
			textProperty().bind(typeProperty.createObservableBinding { "_Type${it.get().let { ": $it" }}" })
			tooltip = Tooltip().also { it.textProperty().bind(typeProperty.createObservableBinding { "Type of the dataset: $it" }) }
		}
		val typeChoiceBox = VBox(typeChoiceButton)
		grid.add(typeChoiceBox, 0, 0)

		/* add open source node */
		GridPane.setMargin(openSourceNode, Insets(0.0, 0.0, 0.0, 30.0))
		GridPane.setHgrow(openSourceNode, Priority.ALWAYS)
		grid.add(openSourceNode, 1, 0)

		val statusBar = TextField().apply {
			background = Background(BackgroundFill(Color.TRANSPARENT, null, null))
		}
		grid.add(statusBar, 0, 1, REMAINING, 1)
		GridPane.setHgrow(statusBar, Priority.ALWAYS)
		statusBar.isEditable = false
		statusBar.textProperty().bind(state.statusProperty)


		metaPanel.bindDataTypeTo(typeProperty)


		dialogPane.content = VBox(10.0).apply {
			children += nameField.textField()
			children += grid
			children += metaPanel.pane
			children += errorInfo

		}
		isResizable = true
		PainteraAlerts.initAppDialog(this)
		dialogPane.scene.window.sizeToScene()
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
		updateFromFile(showDialog(owner))
	}

	private fun updateFromFileChooser() = FileChooser().run {
		initialDirectory = startDirFromSelection()
		extensionFilters.setAll(FileChooser.ExtensionFilter("h5", *H5_EXTENSIONS))
		updateFromFile(showOpenDialog(owner))
	}

	private fun revalidateContainer() {
		/* trigger the re-validation */
		//TODO Caleb: verify this works as intended
		val curSelection = containerSelection ?: return
		containerSelection = null
		containerSelection = curSelection
	}

	override fun errorMessages(): Collection<ObservableValue<String>> {
		val errors = mutableListOf<ObservableValue<String>>()
		errors += nameField.errorMessageProperty()
		with(state) {
			containerState ?: let { errors += SimpleStringProperty("No Valid Container") }
			dimensionsBinding.get()?.takeIf { it.size > 4 }?.let { errors += SimpleStringProperty("Only 3 or 4 dimensions supported. Found ${it.size}") }
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


	companion object {

		private val LOG = KotlinLogging.logger { }

		private fun OpenSourceDialog.startDirFromSelection() = containerSelection
			?.let { path -> File(path).takeIf { it.exists() } }
			?: File(UserDirectories.get().homeDir)

		val menuEntry = OpenDialogMenuEntry {

			BiConsumer<PainteraBaseView, Supplier<String>> { paintera, _ ->
				var openSourceState = OpenSourceState()
				try {
					N5FactoryOpener(openSourceState).backendDialog().apply {
						PainteraAlerts.initAppDialog(this)
						headerText = "Open Source Dataset"
						val openSourceState = showAndWait().nullable ?: return@BiConsumer

						N5OpenSourceHelper.addSource(type, openSourceState, metaPanel.channelInformation().channelSelectionCopy, paintera)
					}
				} catch (e: Exception) {
					LOG.warn(e) {
						val (container, dataset) = openSourceState.run {
							containerState?.uri to datasetPath
						}
						"Unable to add source from $container and dataset $dataset"
					}

					Exceptions.exceptionAlert(Constants.NAME, "Unable to add source", e).apply {
						PainteraAlerts.initAppDialog(this)
						paintera.node.scene?.window?.also { initOwner(it) }
						show()
					}
				}
			}
		}

		fun actionSet(baseView: PainteraBaseView) = painteraActionSet("Open dataset", MenuActionType.AddSource) {
			KEY_PRESSED(PainteraBaseKeys.namedCombinationsCopy(), PainteraBaseKeys.OPEN_SOURCE) {
				onAction { PainteraMenuItems.OPEN_SOURCE.menu.fire() }
				handleException { exception ->
					Exceptions.exceptionAlert(
						Constants.NAME,
						"Unable to show open source menu",
						exception,
						owner = baseView.node.scene?.window
					).show()
				}
			}
		}
	}
}

