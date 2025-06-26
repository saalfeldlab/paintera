package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import bdv.viewer.Source
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Pair
import net.imglib2.img.cell.AbstractCellImg
import net.imglib2.realtransform.AffineTransform3D
import org.controlsfx.control.decoration.Decoration
import org.controlsfx.control.decoration.GraphicDecoration
import org.controlsfx.validation.ValidationMessage
import org.controlsfx.validation.ValidationResult
import org.controlsfx.validation.ValidationSupport
import org.controlsfx.validation.Validator
import org.controlsfx.validation.decoration.GraphicValidationDecoration
import org.janelia.saalfeldlab.fx.SaalFxStyle
import org.janelia.saalfeldlab.fx.ui.DirectoryField
import org.janelia.saalfeldlab.fx.ui.Exceptions.Companion.exceptionAlert
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.bufferNode
import org.janelia.saalfeldlab.fx.ui.NamedNode.Companion.nameIt
import org.janelia.saalfeldlab.fx.ui.ObjectField.Companion.stringField
import org.janelia.saalfeldlab.fx.ui.ObjectField.SubmitOn
import org.janelia.saalfeldlab.fx.ui.SpatialField
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.n5.N5KeyValueWriter
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Paintera.Companion.n5Factory
import org.janelia.saalfeldlab.paintera.Style.ADD_ICON
import org.janelia.saalfeldlab.paintera.Style.REMOVE_ICON
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource
import org.janelia.saalfeldlab.paintera.data.n5.N5DataSource
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataUtils.Companion.createMetadataState
import org.janelia.saalfeldlab.paintera.state.metadata.N5ContainerState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts
import org.janelia.saalfeldlab.util.n5.N5Data
import org.janelia.saalfeldlab.util.n5.N5Helpers
import java.nio.file.Path
import java.util.*


class CreateDataset(private val currentSource: Source<*>?, vararg allSources: SourceState<*, *>) {


	private val mipmapLevels = FXCollections.observableArrayList<MipMapLevel>().also {
		it.addListener( ListChangeListener {  change ->
			if (change.next()) {
				change.addedSubList.forEach { level ->
					level.isLabelMultisetProperty.bind(labelMultiset.selectedProperty())
				}
			}
		})
	}
	private val mipmapLevelsNode by lazy {
		createMipMapLevelsNode(mipmapLevels, FIELD_WIDTH, NAME_WIDTH, *SubmitOn.entries.toTypedArray())
	}

	private val populateFromCurrentSource = MenuItem("_From Current Source").apply {
		onAction = EventHandler {
			it.consume()
			populateFrom(currentSource)
		}
	}


	private val populateFromSource = Menu("_Select Source").apply {
		isVisible = allSources.isNotEmpty()
		allSources.forEach { source ->
			items += MenuItem(source.nameProperty().get()).apply {
				onAction = EventHandler { populateFrom(source.dataSource) }
				isMnemonicParsing = false
			}
		}
	}


	private val setFromButton = MenuButton("_Populate", null, populateFromSource, populateFromCurrentSource)
	private val setFromCurrentBox = HBox(bufferNode(), setFromButton).apply { HBox.setHgrow(children[0], Priority.ALWAYS) }

	private val nameField = object: TextField() {
		var manuallyNamed = false
		override fun paste() {
			super.paste()
			if (text.isNotEmpty())
				manuallyNamed = true
		}

	}.apply {
		maxWidth = Double.MAX_VALUE
		onKeyTyped = EventHandler {
			if (text.isNotEmpty())
				manuallyNamed = true
		}
		textProperty().addListener { _, _, new ->
			if (new.isEmpty())
				manuallyNamed = false
		}
	}

	private val n5Container: DirectoryField = DirectoryField(paintera.projectDirectory.actualDirectory, FIELD_WIDTH)
	private val dataset = stringField("", *SubmitOn.entries.toTypedArray()).apply {
		textField.textProperty().addListener { _, _, newv: String? ->
			if (!nameField.manuallyNamed && newv != null) {
				nameField.text = newv.split("/").toTypedArray().last()
			}
		}
	}


	private val dimensions = SpatialField.longField(1, { it > 0 }, FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	private val blockSize = SpatialField.intField(1, { it > 0 }, FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	private val resolution = SpatialField.doubleField(1.0, { it > 0 }, FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	private val offset = SpatialField.doubleField(0.0, { true }, FIELD_WIDTH, *SubmitOn.entries.toTypedArray())
	private val unitField = TextField("pixel")
	private val labelMultiset = CheckBox().also { it.isSelected = true }
	private val scaleLevels = TitledPane("Scale Levels", mipmapLevelsNode)
	//TODO Caleb: Use a proper grid layout instead of this...
	private val pane = VBox(
		nameIt("Name", NAME_WIDTH, true, nameField),
		nameIt("N5", NAME_WIDTH, true, n5Container.asNode()),
		nameIt("Dataset", NAME_WIDTH, true, dataset.textField),
		nameIt("Dimensions", NAME_WIDTH, false, bufferNode(), dimensions.node),
		nameIt("Block Size", NAME_WIDTH, false, bufferNode(), blockSize.node),
		nameIt("Resolution", NAME_WIDTH, false, bufferNode(), resolution.node),
		nameIt("Offset (physical)", NAME_WIDTH, false, bufferNode(), offset.node),
		nameIt("Unit", NAME_WIDTH, false, bufferNode(), unitField),
		nameIt("", NAME_WIDTH, false, bufferNode(), HBox(Label("Label Multiset Type  "), labelMultiset)),
		setFromCurrentBox,
		scaleLevels
	)

	init {
		mipmapLevels.addListener(ListChangeListener {
			if (mipmapLevels.size == 0) return@ListChangeListener
			while (it.next()) {
				if (it.wasRemoved()) {
					if (it.from == 0) {
						/* s0 was removed, re-base the dimensions based on the new s0 */
						reduceBaseScale(mipmapLevels[0])
					} else if (it.from < mipmapLevels.size) {
						adjustSubsequentScale(it.removed[0]!!, mipmapLevels[it.from])
						provideAbsoluteValues(mipmapLevels, resolution, dimensions)
					}
				} else if (it.wasAdded()) {
					it.addedSubList.forEach { newLevel -> registerLevelDecorations(newLevel) }
					provideAbsoluteValues(mipmapLevels, resolution, dimensions)
				}
			}
		})
		currentSource?.let { populateFrom(it) } ?: let {
			val scale0 = MipMapLevel(1, -1, FIELD_WIDTH, NAME_WIDTH, *SubmitOn.entries.toTypedArray())
			provideAbsoluteValues(listOf(scale0), resolution, dimensions)
			mipmapLevels += scale0
		}
	}

	private class AlignableGraphicValidationDecoration(private val alignment: Pos = Pos.BOTTOM_RIGHT) : GraphicValidationDecoration() {
		override fun createValidationDecorations(message: ValidationMessage?): MutableCollection<Decoration> {
			return mutableListOf(GraphicDecoration(createDecorationNode(message), alignment))
		}
	}

	private fun registerLevelDecorations(level: MipMapLevel) {
		val x = level.dimensions.x
		val y = level.dimensions.y
		val z = level.dimensions.z

		val support = ValidationSupport()
		(support.validationDecorator as? GraphicValidationDecoration)?.let {
			support.validationDecoratorProperty().set(AlignableGraphicValidationDecoration(Pos.BOTTOM_RIGHT))
		}

		support.registerValidator(x.textField, false, Validator<String> { _, _ ->
			ValidationResult.fromErrorIf(x.textField, "Dimension size is zero. Remove this scale level.", x.value.toInt() <= 0)
				?.addWarningIf(x.textField, "Dimension is smaller than block size. Consider removing this scale level.", x.value.toInt() <= blockSize.x.value.toInt())
		})
		support.registerValidator(y.textField, false, Validator<String> { _, _ ->
			ValidationResult.fromErrorIf(y.textField, "Dimension size is zero. Remove this scale level.", y.value.toInt() <= 0)
				?.addWarningIf(y.textField, "Dimension is smaller than block size. Consider removing this scale level.", y.value.toInt() <= blockSize.y.value.toInt())
		})
		support.registerValidator(z.textField, false, Validator<String> { _, _ ->
			ValidationResult.fromErrorIf(z.textField, "Dimension size is zero. Remove this scale level.", z.value.toInt() <= 0)
				?.addWarningIf(z.textField, "Dimension is smaller than block size. Consider removing this scale level.", z.value.toInt() <= blockSize.z.value.toInt())
		})

		blockSize.x.valueProperty().addListener { _, _, _ -> support.revalidate() }
		blockSize.y.valueProperty().addListener { _, _, _ -> support.revalidate() }
		blockSize.z.valueProperty().addListener { _, _, _ -> support.revalidate() }
	}

	private fun reduceBaseScale(newBaseScale: MipMapLevel) {

		val relativeFactors = newBaseScale.relativeDownsamplingFactors.asLongArray().toTypedArray()

		data class ReduceBaseData(
			val dimension: LongProperty,
			val resolution: DoubleProperty,
			val offset: DoubleProperty
		)

		val xData = ReduceBaseData(dimensions.x.valueProperty(), resolution.x.valueProperty(), offset.x.valueProperty())
		val yData = ReduceBaseData(dimensions.y.valueProperty(), resolution.y.valueProperty(), offset.y.valueProperty())
		val zData = ReduceBaseData(dimensions.z.valueProperty(), resolution.z.valueProperty(), offset.z.valueProperty())

		listOf(xData, yData, zData).zip(relativeFactors).forEach { (baseData, factor) ->
			baseData.dimension.value /= factor
			val prevRes = baseData.resolution.value
			baseData.resolution.value *= factor
			baseData.offset.value += (baseData.resolution.value - prevRes) / 2.0
		}

		newBaseScale.relativeDownsamplingFactors.apply {
			x.valueProperty().value = 1
			y.valueProperty().value = 1
			z.valueProperty().value = 1
		}
	}

	private fun adjustSubsequentScale(removedScale: MipMapLevel, nextScale: MipMapLevel) {

		nextScale.relativeDownsamplingFactors.apply {
			removedScale.relativeDownsamplingFactors.let {
				x.valueProperty().value *= it.x.valueProperty().value
				y.valueProperty().value *= it.y.valueProperty().value
				z.valueProperty().value *= it.z.valueProperty().value
			}
		}
	}

	fun showDialog(projectDirectory: String?): Optional<Pair<MetadataState, String>> {
		val metadataStateProp = SimpleObjectProperty<MetadataState>()
		n5Container.directoryProperty().value = Path.of(projectDirectory!!).toFile()
		PainteraAlerts.confirmation("C_reate", "_Cancel").apply {
			Paintera.registerStylesheets(pane)
			headerText = "Create new Label dataset"
			dialogPane.content = pane
			dialogPane.lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION) { e: ActionEvent ->
				val container = n5Container.directoryProperty().value!!
				val dataset = dataset.value ?: ""
				val name = nameField.text
				LOG.debug { "Trying to create empty label dataset `$dataset' in container `$container'" }
				var invalidCause: String? = null
				if (dataset.isNullOrEmpty()) invalidCause = "Dataset not specified"
				if (name.isNullOrEmpty()) invalidCause = invalidCause?.let { "$it, Name not specified" } ?: "Name not specified"
				invalidCause?.let {
					alertIfInvalidInput(it)
					e.consume()
					return@addEventFilter
				}

				/* Remove Scales where downsampling factors are 1 */
				val scaleLevels = mutableListOf<MipMapLevel>()
				mipmapLevels.forEach { level ->
					if (level.relativeDownsamplingFactors.asDoubleArray().reduce { l, r -> l * r } != 1.0)
						scaleLevels.add(level)
				}

				try {
					val writer = n5Factory.newWriter(container.absolutePath)

					if (labelMultiset.isSelected && writer !is N5KeyValueWriter)
						throw UnsupportedOperationException("LabelMultisetType Label dataset only supported for N5 datasets")

					N5Data.createEmptyLabelDataset(
						writer,
						dataset,
						dimensions.asLongArray(),
						blockSize.asIntArray(),
						resolution.asDoubleArray(),
						offset.asDoubleArray(),
						scaleLevels.map { it.downsamplingFactors() }.toTypedArray(),
						unitField.text,
						if (labelMultiset.isSelected) scaleLevels.stream().mapToInt { it.maxNumEntries() }.toArray() else null,
						labelMultiset.isSelected,
						false
					)

					N5Helpers.parseMetadata(writer, true).ifPresent { _ ->
						val containerState = N5ContainerState(writer)
						createMetadataState(containerState, dataset)?.also { metadataStateProp.set(it) }
					}
				} catch (ex: Exception) {
					alertIfError(ex)
					e.consume()
					return@addEventFilter
				}
			}
			mipmapLevels.addListener { change: ListChangeListener.Change<out MipMapLevel> ->
				change.next()
				if (change.wasAdded() || change.wasRemoved()) {
					(pane.scene.window as? Stage)?.sizeToScene()
				}
			}
		}.showAndWait()
		val name = nameField.text
		return Optional.ofNullable(metadataStateProp.get()).map { metadataState: MetadataState -> Pair(metadataState, name) }
	}

	private fun alertIfError(ex: Exception) {
		LOG.error(ex) { "Unable to create new label dataset" }
		exceptionAlert(Constants.NAME, "Unable to create new label dataset: ${ex.message}", ex).show()
	}

	private fun alertIfInvalidInput(reason: String) {
		LOG.warn { reason }
		PainteraAlerts.alert(Alert.AlertType.ERROR).apply {
			headerText = "Unable to create new dataset"
			contentText = reason
		}.showAndWait()
	}

	private fun populateFrom(source: Source<*>?) {
		val metadataSource = when (source ) {
			null -> return
			is N5DataSource<*,*> -> source
			is MaskedSource<*, *> -> source.underlyingSource() as? N5DataSource<*, *>
			else -> null
		}

		metadataSource?.let {
			val blockDims = it.metadataState.datasetAttributes.blockSize
			blockSize.x.value = blockDims[0]
			blockSize.y.value = blockDims[1]
			blockSize.z.value = blockDims[2]
		}
		val data = source.getSource(0, 0)
		dimensions.x.value = data.dimension(0)
		dimensions.y.value = data.dimension(1)
		dimensions.z.value = data.dimension(2)
		if (data is AbstractCellImg<*, *, *, *>) {
			val grid = data.cellGrid
			blockSize.x.value = grid.cellDimension(0)
			blockSize.y.value = grid.cellDimension(1)
			blockSize.z.value = grid.cellDimension(2)
		}
		val transform = AffineTransform3D()
		source.getSourceTransform(0, 0, transform)
		resolution.x.value = transform[0, 0]
		resolution.y.value = transform[1, 1]
		resolution.z.value = transform[2, 2]
		metadataSource?.metadataState?.virtualCrop?.let {
			offset.x.value = it.min(0) * transform[0, 0]
			offset.y.value = it.min(1) * transform[1, 1]
			offset.z.value = it.min(2) * transform[2, 2]
		} ?: let {
			offset.x.value = transform[0, 3]
			offset.y.value = transform[1, 3]
			offset.z.value = transform[2, 3]
		}

		unitField.text = metadataSource?.metadataState?.unit
		setMipMapLevels(source)
	}

	private fun setMipMapLevels(source: Source<*>) {

		/* s0 has relative factors of 1, since s0 is the base */
		val levels = mutableListOf(MipMapLevel(1, -1, FIELD_WIDTH, NAME_WIDTH, *SubmitOn.entries.toTypedArray()))
		val firstTransform = AffineTransform3D()
		source.getSourceTransform(0, 0, firstTransform)
		var previousFactors = doubleArrayOf(firstTransform[0, 0], firstTransform[1, 1], firstTransform[2, 2])
		for (i in 1 until source.numMipmapLevels) {
			val transform = AffineTransform3D()
			source.getSourceTransform(0, i, transform)
			val downsamplingFactors = doubleArrayOf(transform[0, 0], transform[1, 1], transform[2, 2])
			val level = MipMapLevel(2, -1, FIELD_WIDTH, NAME_WIDTH, *SubmitOn.entries.toTypedArray())
			val relativeXFactor = downsamplingFactors[0] / previousFactors[0]
			val relativeYFactor = downsamplingFactors[1] / previousFactors[1]
			val relativeZFactor = downsamplingFactors[2] / previousFactors[2]
			level.relativeDownsamplingFactors.setValues(relativeXFactor, relativeYFactor, relativeZFactor)
			previousFactors = downsamplingFactors
			levels.add(level)
		}
		provideAbsoluteValues(levels, resolution, dimensions)
		levels.removeIf { level ->
			val isFirst = level == levels[0]
			val tooSmall = level.dimensions.asLongArray().zip(blockSize.asLongArray()).all { (dim, block) -> dim <= block  }
			!isFirst && tooSmall
		}
		mipmapLevels.clear()
		mipmapLevels += levels
	}


	companion object {
		private val LOG = KotlinLogging.logger {  }
		private const val FIELD_WIDTH = 75.0
		private const val NAME_WIDTH = 100.0
		private const val ADD_BUTTON = "AddButton"

		@JvmStatic
		fun main(args: Array<String>) {
			val obsLevels = FXCollections.observableArrayList<MipMapLevel>()
			val levels = listOf(
				MipMapLevel(2, -1, 60.0, 60.0),
				MipMapLevel(2, -1, 60.0, 60.0),
				MipMapLevel(2, -1, 60.0, 60.0),
				MipMapLevel(2, -1, 60.0, 60.0),
				MipMapLevel(2, -1, 60.0, 60.0),
				MipMapLevel(2, -1, 60.0, 60.0),
			)
			provideAbsoluteValues(levels, SpatialField.doubleField(4.0, { true }), SpatialField.longField(100, { true }))
			InvokeOnJavaFXApplicationThread {
				val scene = Scene(createMipMapLevelsNode(obsLevels, 60.0, 60.0))
				obsLevels += levels
				SaalFxStyle.registerStylesheets(scene)
				val stage = Stage()
				stage.scene = scene
				stage.show()
			}
		}

		private fun provideAbsoluteValues(mipmapLevels: List<MipMapLevel>, resolution: SpatialField<DoubleProperty>, baseDimensions: SpatialField<LongProperty>) {
			val baseAbsoluteFactors = SpatialField.intField(1, { true }, FIELD_WIDTH, *SubmitOn.entries.toTypedArray()).also { it.editable = false }.also {
				it.x.valueProperty().bind(mipmapLevels[0].relativeDownsamplingFactors.x.valueProperty())
				it.y.valueProperty().bind(mipmapLevels[0].relativeDownsamplingFactors.y.valueProperty())
				it.z.valueProperty().bind(mipmapLevels[0].relativeDownsamplingFactors.z.valueProperty())
			}
			mipmapLevels[0].displayAbsoluteValues(resolution, baseAbsoluteFactors, baseDimensions)

			var previousAbsoluteFactors = baseAbsoluteFactors
			if (mipmapLevels.size > 1) {
				mipmapLevels.subList(1, mipmapLevels.size).forEach { level ->
					val absoluteFactors = SpatialField.intField(1, { true })
					absoluteFactors.x.valueProperty().bind(previousAbsoluteFactors.x.valueProperty().multiply(level.relativeDownsamplingFactors.x.valueProperty()))
					absoluteFactors.y.valueProperty().bind(previousAbsoluteFactors.y.valueProperty().multiply(level.relativeDownsamplingFactors.y.valueProperty()))
					absoluteFactors.z.valueProperty().bind(previousAbsoluteFactors.z.valueProperty().multiply(level.relativeDownsamplingFactors.z.valueProperty()))
					level.displayAbsoluteValues(resolution, absoluteFactors, baseDimensions)
					previousAbsoluteFactors = absoluteFactors
				}
			}
		}

		private fun createMipMapLevelsNode(
			levels: ObservableList<MipMapLevel>,
			fieldWidth: Double,
			nameWidth: Double,
			vararg submitOn: SubmitOn
		) = VBox().apply {
			val addButton = Button().apply {
				addStyleClass(ADD_ICON)
			}
			addButton.onAction = EventHandler { event ->
				event.consume()
				val newLevel = MipMapLevel(2, -1, fieldWidth, nameWidth, *submitOn).apply {
					levels.last()?.also { prevLevel ->
						displayAbsoluteValues(prevLevel.resolution, prevLevel.absoluteDownsamplingFactors, prevLevel.baseDimensions)
					}
				}
				levels.add(newLevel)
			}

			children += HBox(addButton).apply {
				id = ADD_BUTTON
				alignment = Pos.TOP_RIGHT
			}
			levels.addListener { _: ListChangeListener.Change<out MipMapLevel> ->
				InvokeOnJavaFXApplicationThread {
					children.filter { it.id != ADD_BUTTON }
						.filterIsInstance<Pane>()
						.forEach { it.children.clear() }
					children.removeIf { it.id != ADD_BUTTON }
					for (i in levels.indices) {
						val level: MipMapLevel = levels[i]
						val scaleLabel = Label("Scale $i: ")
						scaleLabel.minWidth = Label.USE_PREF_SIZE
						if (i == 0) {
							level.showHeader.value = true
							level.relativeDownsamplingFactors.editable = false
						}
						val removeButton = Button().apply {
							addStyleClass(REMOVE_ICON)
							onAction = EventHandler {
								it.consume()
								levels.remove(level)
							}
							disableProperty().bind(Bindings.size(levels).lessThanOrEqualTo(1))
						}
						val levelNode = level.makeNode()
						children += HBox(scaleLabel, levelNode, removeButton).apply {
							alignment = Pos.BOTTOM_LEFT
						}
						HBox.setHgrow(levelNode, Priority.ALWAYS)
					}
				}
			}
		}
	}
}
