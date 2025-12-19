package org.janelia.saalfeldlab.paintera.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.ContentDisplay
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.*
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.MatchSelection
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.paintera.util.logging.LogUtils
import org.kordamp.ikonli.fontawesome.FontAwesome.*
import org.slf4j.LoggerFactory

class LoggingConfigNode(private val config: LoggingConfig) {

	private val unmodifiableLoggerLevels = config.unmodifiableLoggerLevels

	val node: Node
		get() {
			val rootLevelChoiceBox = logLevelChoiceBox(config.rootLoggerLevelProperty)
			val loggerLevelGrid = GridPane()
			loggerLevelGrid.columnConstraints.setAll(
				ColumnConstraints().also { it.hgrow = Priority.ALWAYS },
				ColumnConstraints()
			)
			unmodifiableLoggerLevels.addListener(MapChangeListener { loggerLevelGrid.setupLevelConfig(rootLevelChoiceBox) })
			loggerLevelGrid.setupLevelConfig(rootLevelChoiceBox)

			val contents = VBox(
				toggleLogEnableNode,
				Separator(Orientation.HORIZONTAL),
				loggerLevelGrid
			)

			val helpDialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).apply {
				headerText = "Configure Paintera logging."
			}

			val tpGraphics = HBox(
				Label("Logging"),
				NamedNode.bufferNode(),
				Button("").apply {
					addStyleClass(Style.HELP_ICON)
					onAction = EventHandler { helpDialog.show() }
				}
			).also {
				it.alignment = Pos.CENTER
			}


			return TitledPanes.createCollapsed(null, contents).apply {
				graphic = tpGraphics
				contentDisplay = ContentDisplay.GRAPHIC_ONLY
				alignment = Pos.CENTER_RIGHT
			}
		}

	private val toggleLogEnableNode: Node
		get() {
			val isEnabledCheckBox = CheckBox("Enable logging").also {
				it.selectedProperty().bindBidirectional(config.isLoggingEnabledProperty)
			}
			val isLoggingToConsoleEnabled = CheckBox("Log to console").apply {
				selectedProperty().bindBidirectional(config.isLoggingToConsoleEnabledProperty)
				disableProperty().bind(config.isLoggingEnabledProperty.not())
			}
			val isLoggingToFileEnabled = CheckBox("Log to file").apply {
				selectedProperty().bindBidirectional(config.isLoggingToFileEnabledProperty)
				disableProperty().bind(config.isLoggingEnabledProperty.not())
				tooltip = Tooltip("Log file located at `${LogUtils.painteraLogFilePath}'")
				contentDisplay = ContentDisplay.RIGHT
				graphicTextGap = 25.0
				graphic = Buttons.withTooltip(null, "Copy log file path (`${LogUtils.painteraLogFilePath}') to clipboard") {
					Clipboard.getSystemClipboard().setContent(ClipboardContent().also { content -> content.putString(LogUtils.painteraLogFilePath) })
				}.apply { addStyleClass(Style.fontAwesome(COPY)) }
			}
			return VBox(
				isEnabledCheckBox,
				isLoggingToConsoleEnabled,
				isLoggingToFileEnabled
			)
		}

	private fun logLevelChoiceBox(logLevelProperty: ObjectProperty<Level>?): ChoiceBox<Level> {
		val choiceBox = ChoiceBox(FXCollections.observableList(LogUtils.Logback.Levels.levels))
		choiceBox.value = LoggingConfig.DEFAULT_LOG_LEVEL
		logLevelProperty?.let { choiceBox.valueProperty().bindBidirectional(it) }
		return choiceBox
	}

	private fun GridPane.setupLevelConfig(rootLoggerLevelChoiceBox: ChoiceBox<Level>) {

		children.clear()

		add(Labels.withTooltip(Logger.ROOT_LOGGER_NAME, "Root logger"), 0, 0)
		add(rootLoggerLevelChoiceBox, 1, 0)

		val keys = unmodifiableLoggerLevels.keys
		val sortedKeys = keys.sorted()

		sortedKeys.forEachIndexed { index, name ->
			unmodifiableLoggerLevels[name]?.let { level ->
				val removeButton = Buttons.withTooltip(null, "Unset level setting for logger `$name'.") {
					config.unsetLogLevelFor(name)
				}
				removeButton.addStyleClass(Style.REMOVE_ICON)
				index.let { it + 1 }.let { row ->
					add(Labels.withTooltip(name), 0, row)
					add(logLevelChoiceBox(level), 1, row)
					add(removeButton, 2, row)
				}
			}
		}

		val loggerList = FXCollections.observableArrayList<String>()
		val updateLoggerList = {
			(LoggerFactory.getILoggerFactory() as? LoggerContext)?.let {
				loggerList.setAll(it.loggerList.map { logger -> logger.name }.toList())
			}
		}
		val newLoggerField = TextField("")
		val matcherField = MatchSelection.fuzzyTop(loggerList, { name -> newLoggerField.text = name }, 5)
		matcherField.emptyBehavior = MatchSelection.EmptyBehavior.MATCH_NONE
		matcherField.promptText = "Search for Loggers..."
		matcherField.focusedProperty().addListener { _, _, focused -> if (focused) updateLoggerList() }

		val newLogLevelChoiceBox = logLevelChoiceBox(null)
		val newLoggerButton = Buttons
			.withTooltip(null) { config.setLogLevelFor(newLoggerField.text, newLogLevelChoiceBox.value) }
			.apply { addStyleClass(Style.ADD_ICON)}
		val listener = InvalidationListener {
			updateLoggerList()
			val name = newLoggerField.text
			val isRootLoggerName = LogUtils.rootLogger.name == name
			val isExistingLogger = name in keys
			val isValidLoggerName = !isExistingLogger && !isRootLoggerName && newLoggerField.text?.isNotEmpty() == true
			newLoggerButton.isDisable = !isValidLoggerName

			when {
				isValidLoggerName -> newLoggerButton.tooltip = Tooltip("Add level setting for logger `$name'.")
				isRootLoggerName -> newLoggerButton.tooltip = Tooltip("Cannot add `$name' because the name is reserved for the root logger.")
				isExistingLogger -> newLoggerButton.tooltip = Tooltip("Cannot add `$name' because it is already configured.")
				else -> newLoggerButton.tooltip = Tooltip("Add level setting for logger (specify logger name)")
			}

		}

		sortedKeys.size.let { it + 1 }.let { row ->
			newLoggerField.textProperty().addListener(listener)
			listener.invalidated(newLoggerField.textProperty())
			add(newLoggerField, 0, row)
			add(newLogLevelChoiceBox, 1, row)
			add(newLoggerButton, 2, row)
			add(matcherField, 0, row + 1, GridPane.REMAINING, GridPane.REMAINING)
			GridPane.setHgrow(matcherField, Priority.ALWAYS)
			GridPane.setVgrow(matcherField, Priority.ALWAYS)
			matcherField.maxWidthProperty().bind(widthProperty())
		}
	}

}
