package org.janelia.saalfeldlab.paintera.config

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.shape.CullFace
import javafx.scene.shape.DrawMode
import javafx.stage.FileChooser
import org.janelia.saalfeldlab.fx.Labels
import org.janelia.saalfeldlab.fx.TitledPanes
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormat
import org.janelia.saalfeldlab.paintera.ui.CloseButton
import org.janelia.saalfeldlab.paintera.ui.FontAwesome
import org.janelia.saalfeldlab.paintera.meshes.io.TriangleMeshFormatService
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import java.util.function.DoublePredicate
import java.util.stream.Collectors

class ArbitraryMeshConfigNode @JvmOverloads constructor(
		triangleMeshFormat: TriangleMeshFormatService,
		val config: ArbitraryMeshConfig = ArbitraryMeshConfig()) : TitledPane("Triangle Meshes", null) {

    private val isVisibleCheckbox = CheckBox()
			.also { it.selectedProperty().bindBidirectional(config.isVisibleProperty) }

    private val meshGroup = Group()
			.also { it.visibleProperty().bindBidirectional(isVisibleCheckbox.selectedProperty()) }

    private val nodeMap = HashMap<ArbitraryMeshConfig.MeshInfo, Node>()

    private val meshConfigs = VBox()

    private val addButton = Button(null).also { it.graphic = FontAwesome[FontAwesomeIcon.PLUS, 2.0] }

    init {
        this.config.unmodifiableMeshes.addListener(InvalidationListener { update() })

        addButton.setOnAction { e ->
            val dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
            (dialog.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_OK"
            (dialog.dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
            dialog.headerText = "Open mesh from file"
            val formats = FXCollections.observableArrayList(TriangleMeshFormat.availableFormats(triangleMeshFormat))
            val extensionFormatMapping = TriangleMeshFormat.extensionsToFormatMapping(triangleMeshFormat)
            val formatChoiceBox = ComboBox(formats)
            formatChoiceBox.promptText = "Format"

            formatChoiceBox.setCellFactory {
				object : ListCell<TriangleMeshFormat>() {
                    override fun updateItem(item: TriangleMeshFormat?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (item == null || empty) {
                            graphic = null
                        } else {
                            text = String.format("%s %s", item.formatName(), item.knownExtensions())
                        }
                    }
                }
            }
            formatChoiceBox.setButtonCell(formatChoiceBox.cellFactory.call(null))

            val lastPath = config.lastPathProperty().get()
            val path = TextField(null)
            path.tooltip = Tooltip()
            path.tooltip.textProperty().bind(path.textProperty())
            path.textProperty().addListener { obs, oldv, newv ->
                if (newv == null)
                    formatChoiceBox.setValue(null)
                else {
                    val split = newv.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val extension = split[split.size - 1]
                    formatChoiceBox.setValue(Optional.ofNullable(extensionFormatMapping[extension]).map { l -> l[0] }.orElse(null))
                }
            }
            path.text = lastPath?.toAbsolutePath()?.toString()
            val isNull = path.textProperty().isNull
            dialog.dialogPane.lookupButton(ButtonType.OK).disableProperty().bind(isNull)
            path.isEditable = false
            val browseButton = Button("_Browse")
            val chooser = FileChooser()
            chooser.initialDirectory = lastPath?.parent?.toFile()
            val newPath = SimpleObjectProperty<Path>()
            browseButton.setOnAction { ev ->
                val newFile = chooser.showOpenDialog(dialog.owner)
                if (newFile != null) {
                    newPath.set(newFile.toPath())
                    path.text = newPath.get().toAbsolutePath().toString()
                }
            }
            val contents = GridPane()
            contents.add(path, 0, 0)
            contents.add(browseButton, 1, 0)
            contents.add(formatChoiceBox, 1, 1)
            GridPane.setHgrow(path, Priority.ALWAYS)
            browseButton.maxWidth = 120.0
            formatChoiceBox.maxWidth = 120.0
            browseButton.tooltip = Tooltip("Browse for mesh file")

            dialog.dialogPane.content = contents
            val button = dialog.showAndWait()
            if (ButtonType.OK == button.orElse(ButtonType.CANCEL) && newPath.get() != null) {
                try {
                    config.lastPathProperty().set(newPath.get())
                    config.addMesh(ArbitraryMeshConfig.MeshInfo(newPath.get(), formatChoiceBox.value))
                } catch (ex: Exception) {
                    Exceptions.exceptionAlert(
							Paintera.NAME,
							String.format("Unable to load mesh at path %s", newPath.name),
							ex)
                }

            }
        }

        content = meshConfigs
        isExpanded = false
        padding = Insets.EMPTY
        val hbox = HBox(isVisibleCheckbox, Label("Triangle Meshes"))
        hbox.alignment = Pos.CENTER
        TitledPanes.graphicsOnly(this, hbox, addButton)

        meshConfigs.padding = Insets.EMPTY

        update()
    }

    private fun update() {
        val meshInfos = ArrayList(this.config.unmodifiableMeshes)
        val toRemoveFromNodeMap = nodeMap
                .keys
                .stream()
                .filter { meshInfos.contains(it) }
				.collect(Collectors.toSet())
        toRemoveFromNodeMap.forEach(Consumer { nodeMap.remove(it) })

        for (meshInfo in meshInfos) {
            if (!nodeMap.containsKey(meshInfo)) {
                val tp = TitledPanes.createCollapsed(null, null)
                val visibleBox = CheckBox()
                visibleBox.selectedProperty().bindBidirectional(meshInfo.isVisibleProperty)
                val nameField = TextField(meshInfo.nameProperty().get())
                nameField.textProperty().bindBidirectional(meshInfo.nameProperty())
                tp.padding = Insets.EMPTY

                val prefCellWidth = 50.0

                val settingsGrid = GridPane()
                val colorPicker = ColorPicker()
                colorPicker.valueProperty().bindBidirectional(meshInfo.colorProperty())
                settingsGrid.add(Labels.withTooltip("Color"), 0, 0)
                settingsGrid.add(colorPicker, 3, 0)
                colorPicker.prefWidth = prefCellWidth

                val translationX = NumberField.doubleField(0.0, DoublePredicate { true }, *ObjectField.SubmitOn.values())
                val translationY = NumberField.doubleField(0.0, DoublePredicate { true }, *ObjectField.SubmitOn.values())
                val translationZ = NumberField.doubleField(0.0, DoublePredicate { true }, *ObjectField.SubmitOn.values())
                translationX.valueProperty().bindBidirectional(meshInfo.translateXProperty())
                translationY.valueProperty().bindBidirectional(meshInfo.translateYProperty())
                translationZ.valueProperty().bindBidirectional(meshInfo.translateZProperty())
                settingsGrid.add(Labels.withTooltip("Translation"), 0, 1)
                settingsGrid.add(translationX.textField(), 1, 1)
                settingsGrid.add(translationY.textField(), 2, 1)
                settingsGrid.add(translationZ.textField(), 3, 1)
                translationX.textField().tooltip = Tooltip()
                translationY.textField().tooltip = Tooltip()
                translationZ.textField().tooltip = Tooltip()
                translationX.textField().tooltip.textProperty().bind(translationX.textField().textProperty())
                translationY.textField().tooltip.textProperty().bind(translationY.textField().textProperty())
                translationZ.textField().tooltip.textProperty().bind(translationZ.textField().textProperty())
                translationX.textField().prefWidth = prefCellWidth
                translationY.textField().prefWidth = prefCellWidth
                translationZ.textField().prefWidth = prefCellWidth


                val scale = NumberField.doubleField(1.0, DoublePredicate { it > 0.0 }, *ObjectField.SubmitOn.values())
                scale.valueProperty().bindBidirectional(meshInfo.scaleProperty())
                settingsGrid.add(Labels.withTooltip("Scale"), 0, 2)
                settingsGrid.add(scale.textField(), 3, 2)
                scale.textField().prefWidth = prefCellWidth

                val drawMode = ChoiceBox(FXCollections.observableArrayList(*DrawMode.values()))
                drawMode.valueProperty().bindBidirectional(meshInfo.drawModeProperty())
                settingsGrid.add(Labels.withTooltip("Draw Mode"), 0, 3)
                settingsGrid.add(drawMode, 3, 3)
                drawMode.prefWidth = prefCellWidth
                drawMode.tooltip = Tooltip("Select draw mode")

                val cullFace = ChoiceBox(FXCollections.observableArrayList(*CullFace.values()))
                cullFace.valueProperty().bindBidirectional(meshInfo.cullFaceProperty())
                settingsGrid.add(Labels.withTooltip("Cull Face"), 0, 4)
                settingsGrid.add(cullFace, 3, 4)
                cullFace.prefWidth = prefCellWidth
                cullFace.tooltip = Tooltip("Select cull face")

                tp.content = settingsGrid

                val removeButton = Button(null).also { it.graphic = CloseButton.createFontAwesome(2.0) }
                removeButton.setOnAction { e -> config.removeMesh(meshInfo) }

                val hbox = HBox(visibleBox, nameField)
                hbox.alignment = Pos.CENTER
                HBox.setHgrow(nameField, Priority.ALWAYS)
                TitledPanes.graphicsOnly(tp, hbox, removeButton)

                nodeMap[meshInfo] = tp
            }
        }

        val meshes = meshInfos
                .stream()
                .map { it.getMeshView() }
				.collect(Collectors.toList())

        val configNodes = meshInfos
                .stream()
                .map { nodeMap[it] }
				.collect(Collectors.toList())

        meshConfigs.children.setAll(configNodes)
        meshGroup.children.setAll(meshes)
    }

    fun getMeshGroup(): Node {
        return this.meshGroup
    }
}
