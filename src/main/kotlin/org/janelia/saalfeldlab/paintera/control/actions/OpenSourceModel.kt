package org.janelia.saalfeldlab.paintera.control.actions

import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections.observableMap
import javafx.collections.FXCollections.synchronizedObservableMap
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.n5.universe.N5TreeNode
import org.janelia.saalfeldlab.n5.universe.metadata.SpatialMetadata
import org.janelia.saalfeldlab.n5.universe.metadata.axes.Axis
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class SourceType { RAW, LABEL }

interface OpenSourceModel {

	val containerSelectionProperty: StringProperty
	val containerStateProperty: ReadOnlyObjectProperty<N5ContainerState?>
	val activeNodeProperty: ObjectProperty<N5TreeNode?>
	val activeMetadataProperty: ObservableValue<SpatialMetadata?>
	val metadataStateBinding: ObservableValue<MetadataState?>
	val validDatasets: SimpleMapProperty<String, N5TreeNode>
	val statusProperty: StringProperty
	val sourceNameProperty: StringProperty
	val isBusyProperty: BooleanProperty
	val typeProperty: ObjectProperty<SourceType>
	val channelSelectionProperty: ObjectProperty<IntArray>

	val containerState get() = containerStateProperty.value
	var activeNode: N5TreeNode?
	val metadataState get() = metadataStateBinding.value
	var sourceName: String
	var type: SourceType
	var channelSelection: IntArray

	fun reparseSelection(selection: String)

	companion object {

		fun OpenSourceModel.getDialog(header: String = "Open Source Dataset"): Dialog<OpenSourceModel> {
			return Dialog<OpenSourceModel>().apply {
				title = Constants.NAME
				headerText = header
				isResizable = true
				/* button config*/
				dialogPane.buttonTypes += arrayOf(ButtonType.CANCEL, ButtonType.OK)
				(dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel"
				(dialogPane.lookupButton(ButtonType.OK) as Button).text = "_OK"
				/* get result if OK else null */
				resultConverter = { if (it == ButtonType.OK) this@getDialog else null }

				bindDialog(this)
				initAppDialog()

				dialogPane.scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED) { event ->
					if (event.code == javafx.scene.input.KeyCode.ESCAPE) {
						result = null
						close()
						event.consume()
					}
				}
				dialogPane.scene.window.sizeToScene()
			}
		}

		fun OpenSourceModel.bindDialog(dialog: Dialog<*>) = dialog.apply {
			val ui = OpenSourceUI(this@bindDialog)
			dialogPane.content = ui
			(dialogPane.lookupButton(ButtonType.OK) as Button).apply {
				disableProperty().unbind()
				disableProperty().bind(ui.hasErrorProperty)
			}
		}

		fun default(): OpenSourceModel = DefaultOpenSourceModel()
	}
}

internal open class DefaultOpenSourceModel : OpenSourceModel {

	override val isBusyProperty: BooleanProperty = SimpleBooleanProperty(false)

	override val containerSelectionProperty = SimpleStringProperty()

	private val containerStatePropertyWrapper = ReadOnlyObjectWrapper<N5ContainerState?>(null)
	var writableContainerState by containerStatePropertyWrapper.nullable()

	override val containerStateProperty: ReadOnlyObjectProperty<N5ContainerState?> = containerStatePropertyWrapper.readOnlyProperty!!

	override val activeNodeProperty = SimpleObjectProperty<N5TreeNode?>()
	override var activeNode by activeNodeProperty.nullable()

	override val activeMetadataProperty: ObservableValue<SpatialMetadata?> = activeNodeProperty.map { it?.metadata as? SpatialMetadata }!!

	override val typeProperty = SimpleObjectProperty(SourceType.RAW)
	override var type by typeProperty.nonnull()

	override val channelSelectionProperty = SimpleObjectProperty(intArrayOf())
	override var channelSelection by channelSelectionProperty.nonnull()

	override fun reparseSelection(selection: String) {
		throw NotImplementedError("This should only be used as an override delegate, or base class")
	}

	override val metadataStateBinding: ObservableValue<MetadataState?> = activeMetadataProperty.createObservableBinding(containerStateProperty) {
		val metadata = activeMetadataProperty.value ?: return@createObservableBinding null
		val container = containerStateProperty.value ?: return@createObservableBinding null
		MetadataUtils.createMetadataState(container, metadata)
	}.apply {
		/* auto-reset channel selection when the metadata changes */
		subscribe { it ->
			val dimensions = it?.datasetAttributes?.dimensions ?: return@subscribe
			if (dimensions.size != 4)
				return@subscribe
			val channelIdx = it.axes.indexOfFirst { it.type == Axis.CHANNEL }.takeUnless { it == -1 } ?: 3

			channelSelection = IntArray(max(dimensions[channelIdx].toInt(), 0)) { it }
		}
	}

	override val sourceNameProperty = SimpleStringProperty().also { prop ->
		activeNodeProperty.subscribe { _ ->
			prop.value = arrayOf(activeNode?.path, containerState?.uri?.path)
				.firstNotNullOfOrNull { it?.split("/")?.lastOrNull { it.isNotEmpty() } }
		}
	}
	override var sourceName: String by sourceNameProperty.nonnull()

	override val validDatasets = SimpleMapProperty<String, N5TreeNode>(synchronizedObservableMap(observableMap(ConcurrentHashMap())))
	override val statusProperty = SimpleStringProperty()
}
