package org.janelia.saalfeldlab.paintera.state.raw

import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ColorPicker
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.util.Subscription
import org.controlsfx.control.ToggleSwitch
import org.janelia.saalfeldlab.fx.ui.ExpandingRowTableView
import org.janelia.saalfeldlab.fx.ui.NamedNode
import org.janelia.saalfeldlab.fx.ui.NumberField
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.ui.ObjectField
import org.janelia.saalfeldlab.net.imglib2.converter.ARGBCompositeColorConverter
import org.janelia.saalfeldlab.paintera.Style
import org.janelia.saalfeldlab.paintera.addStyleClass
import org.janelia.saalfeldlab.paintera.control.IntensityThreshold
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.source.ActiveChannelsNode
import org.janelia.saalfeldlab.util.Colors
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread

/**
 * The color conversion of a raw source with more than one channel: which channels are active in the composite and the
 * color, range and opacity of every dataset channel
 */
internal class ChannelCompositeNode(private val composition: ChannelComposition<*, *>) : TitledPane() {

    private val numChannels = composition.numChannels
    private val converter = composition.converter

    private val globalRange = GlobalRangeNode(composition)

    private val activeColumn = TableColumn<Int, Int>().apply {
        setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
        setCellFactory { ActiveCell() }
        isSortable = false
        isReorderable = false
        isResizable = false
        minWidth = ACTIVE_COLUMN_WIDTH
        maxWidth = ACTIVE_COLUMN_WIDTH
    }
    private val idxColumn = TableColumn<Int, Int>().apply {
        setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
        setCellFactory { IdxCell() }
        isReorderable = false
        isResizable = false
        minWidth = IDX_COLUMN_WIDTH
        maxWidth = IDX_COLUMN_WIDTH
    }
    private val channelColumn = TableColumn<Int, Int>().apply {
        setCellValueFactory { ReadOnlyObjectWrapper(it.value) }
        setCellFactory { ChannelCell() }
        isSortable = false
        isReorderable = false
    }

    /* the panes currently shown, by channel */
    private val panes = mutableMapOf<Int, ChannelPane>()

    private val hideInactive = SimpleBooleanProperty(false)
    private val visibleChannels = FilteredList(FXCollections.observableArrayList((0 until numChannels).toList()))

    private val channelList = ExpandingRowTableView<Int>({ channel, width -> panes[channel]?.prefHeight(width) }, MAX_ROWS).apply {
        columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS
        selectionModel.isCellSelectionEnabled = false

        columns.setAll(activeColumn, idxColumn, channelColumn)
        items = SortedList(visibleChannels).also { it.comparatorProperty().bind(comparatorProperty()) }

        /* always sorted by index; swallow the unsorted "isEmpty" sort order */
        sortOrder.setAll(idxColumn)
        sortOrder.addListener(ListChangeListener {
            if (sortOrder.isEmpty())
                InvokeOnJavaFXApplicationThread {
                    idxColumn.sortType = TableColumn.SortType.ASCENDING
                    sortOrder.setAll(idxColumn)
                }
        })
    }

    private val activeChannels: List<Int>
        get() = composition.activeChannels

    private fun setActiveChannels(channels: List<Int>) {
        composition.activeChannels = channels
    }

    init {
        val activeBox = ActiveChannelsNode(numChannels, composition.activeChannelsProperty)

        /* the color picker is inside the button */
        val spreadPicker = ColorPicker(Color.MAGENTA).apply {
            style = "-fx-color-label-visible: false;"
            tooltip = Tooltip("the first channel's color; the others step around the hue circle from it")
            setOnAction { setColors(ChannelColors.Spread(value)) }
        }
        val spreadButton = Button("Spread", spreadPicker).apply {
            contentDisplay = ContentDisplay.LEFT
            tooltip = Tooltip("golden-angle hues from the picked color, like label ids")
            setOnAction { setColors(ChannelColors.Spread(spreadPicker.value)) }
        }
        val colorRow = HBox(
            5.0,
            Button("CMY").apply {
                tooltip = Tooltip("cyan, magenta, yellow, then golden-angle hues")
                setOnAction { setColors(ChannelColors.CMY) }
            },
            Button("White + CMY").apply {
                tooltip = Tooltip("white, cyan, magenta, yellow, then golden-angle hues")
                setOnAction { setColors(ChannelColors.WhiteCMY) }
            },
            spreadButton
        ).apply { alignment = Pos.CENTER_LEFT }

        /* the opacity of the composed result */
        val opacitySlider = NumericSliderWithField(0.0, 1.0, converter.alphaProperty().get()).apply {
            valueProperty.bindBidirectional(converter.alphaProperty())
            textField.minWidth = 48.0
            textField.maxWidth = 48.0
            HBox.setHgrow(slider, Priority.ALWAYS)
        }
        val opacityRow = HBox(5.0, Label("Opacity"), opacitySlider.slider, opacitySlider.textField).apply { alignment = Pos.CENTER_LEFT }

        activeColumn.graphic = CheckBox().apply {
            tooltip = Tooltip("All channels active / none")
            composition.activeChannelsProperty.subscribe { channels ->
                isSelected = channels.size == numChannels
                isIndeterminate = channels.isNotEmpty() && channels.size < numChannels
            }
            setOnAction { setActiveChannels(if (isSelected) (0 until numChannels).toList() else emptyList()) }
        }

        channelColumn.graphic = HBox(5.0).apply {
            children.setAll(
                Label("Channel"),
                NamedNode.bufferNode(),
                CheckBox("Hide inactive").apply { selectedProperty().bindBidirectional(hideInactive) }
            )
            alignment = Pos.CENTER_LEFT
            prefWidthProperty().bind(channelColumn.widthProperty())
        }
        fun refreshRows() = visibleChannels.setPredicate { !hideInactive.get() || it in activeChannels }
        hideInactive.subscribe { _, _ -> refreshRows() }
        composition.activeChannelsProperty.subscribe { _, _ -> refreshRows() }

        graphic = HBox(Label("Color Conversion"), NamedNode.bufferNode()).apply { alignment = Pos.CENTER }
        content = VBox(
            5.0,
            activeBox,
            Separator(),
            globalRange,
            opacityRow,
            Separator(),
            colorRow,
            channelList
        )
        contentDisplay = ContentDisplay.GRAPHIC_ONLY
        alignment = Pos.CENTER_RIGHT
        isExpanded = false
    }

    private inner class IdxCell : TableCell<Int, Int>() {

        init {
            alignment = Pos.CENTER
        }

        override fun updateItem(item: Int?, empty: Boolean) {
            super.updateItem(item, empty)
            text = item?.takeUnless { empty }?.toString()
        }
    }

    private inner class ActiveCell : TableCell<Int, Int>() {

        private val checkBox = CheckBox().apply {
            tooltip = Tooltip("Active in the composite")
            setOnAction {
                val channel = item ?: return@setOnAction
                val active = activeChannels
                setActiveChannels(if (channel in active) active - channel else active + channel)
            }
        }

        init {
            style = "-fx-padding: 0px"
            alignment = Pos.CENTER
            composition.activeChannelsProperty.subscribe { channels -> item?.let { checkBox.isSelected = it in channels } }
        }

        override fun updateItem(item: Int?, empty: Boolean) {
            super.updateItem(item, empty)
            text = null
            if (empty || item == null) {
                graphic = null
                return
            }
            checkBox.isSelected = item in activeChannels
            graphic = checkBox
        }
    }

    private inner class ChannelCell : TableCell<Int, Int>() {

        private var pane: ChannelPane? = null

        init {
            style = "-fx-padding: 0px"
            /* the virtual flow removes cells and puts them back without updateItem; a pane released on
             * the way out would stay on screen stale, so it is rebuilt on the way back in */
            sceneProperty().subscribe { _, scene ->
                if (scene == null)
                    release()
                else
                    showPane()
            }
        }

        private fun release() {
            pane?.let {
                it.release()
                panes.remove(it.channel, it)
            }
            pane = null
        }

        private fun showPane() {
            val channel = item
            if (isEmpty || channel == null) {
                graphic = null
                release()
                return
            }
            if (pane?.channel != channel) {
                release()
                pane = ChannelPane(channel).also {
                    it.prefWidthProperty().bind(widthProperty().subtract(2))
                    panes[channel] = it
                }
            }
            graphic = pane
        }

        override fun updateItem(item: Int?, empty: Boolean) {
            super.updateItem(item, empty)
            text = null
            showPane()
        }
    }

    private inner class ChannelPane(val channel: Int) : TitledPane() {

        private val minProperty = converter.minProperty(channel)
        private val maxProperty = converter.maxProperty(channel)
        private val alpha = converter.channelAlphaProperty(channel)
        private val minField = NumberField.doubleField(minProperty.get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
        private val maxField = NumberField.doubleField(maxProperty.get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
        private val alphaSlider = NumericSliderWithField(0.0, 1.0, alpha.get())
        private val subscriptions: Subscription

        init {
            val argb = converter.colorProperty(channel)
            val colorPicker = ColorPicker(Colors.toColor(argb.get()))
            val colorToConverter = colorPicker.valueProperty().subscribe { _, color -> argb.value = Colors.toARGBType(color) }
            val converterToColor = argb.subscribe { _, color -> colorPicker.value = Colors.toColor(color) }

            val disableIfInactive = composition.activeChannelsProperty.subscribe { channels -> isDisable = channel !in channels }

            val reset = iconButton("intensity-reset-min-max", "Reset min and max from the dataset metadata") {
                IntensityThreshold.resetChannelMinMax(composition, channel)
            }
            val auto = iconButton("intensity-auto-min-max", "Estimate min and max from the values on screen") {
                paintera.baseView.mostRecentFocusHolder.value?.viewer()?.let { IntensityThreshold.autoChannelMinMax(composition, channel, it) }
            }
            /* with a global range the toolbar's Reset and Auto apply to every active channel */
            listOf(reset, auto).forEach { it.disableProperty().bind(globalRange.enabledProperty) }

            minField.valueProperty().bindBidirectional(minProperty)
            maxField.valueProperty().bindBidirectional(maxProperty)
            minField.textField.tooltip = Tooltip("min")
            maxField.textField.tooltip = Tooltip("max")
            listOf(minField.textField, maxField.textField).forEach { it.disableProperty().bind(globalRange.enabledProperty) }

            alphaSlider.valueProperty.bindBidirectional(alpha)
            alphaSlider.textField.minWidth = 48.0
            alphaSlider.textField.maxWidth = 48.0
            Tooltip.install(alphaSlider.slider, Tooltip("opacity"))
            HBox.setHgrow(alphaSlider.slider, Priority.ALWAYS)

            content = VBox(
                3.0,
                GlobalRangeNode.minMaxGrid(minField.textField, maxField.textField),
                VBox(2.0, Label("Opacity"), HBox(5.0, alphaSlider.slider, alphaSlider.textField).apply { alignment = Pos.CENTER_LEFT })
            )
            graphic = HBox(5.0, colorPicker, NamedNode.bufferNode(), reset, auto).apply { alignment = Pos.CENTER_LEFT }
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            alignment = Pos.CENTER_RIGHT
            isExpanded = false
            minWidth = 0.0
            subscriptions = Subscription.combine(colorToConverter, converterToColor, disableIfInactive)
        }

        fun release() {
            minField.valueProperty().unbindBidirectional(minProperty)
            maxField.valueProperty().unbindBidirectional(maxProperty)
            alphaSlider.valueProperty.unbindBidirectional(alpha)
            minField.textField.disableProperty().unbind()
            maxField.textField.disableProperty().unbind()
            subscriptions.unsubscribe()
        }
    }

    /* reuse the toolbar's reset/auto buttons */
    private fun iconButton(styleClass: String, tooltipText: String, onAction: () -> Unit) = Button(null).apply {
        addStyleClass("channel-control", styleClass)
        graphic = Region().apply { addStyleClass(Style.TOOLBAR_GRAPHIC) }
        tooltip = Tooltip(tooltipText)
        setOnAction { onAction() }
    }

    /* only the active channels, in their composite order */
    private fun setColors(colors: ChannelColors) = colors.applyTo(converter, activeChannels)

    companion object {
        private const val ACTIVE_COLUMN_WIDTH = 28.0
        private const val IDX_COLUMN_WIDTH = 32.0
        private const val MAX_ROWS = 8

    }
}

/** A toggle and a min/max pair; while on, every channel of [composition] shares them */
internal class GlobalRangeNode(private val composition: ChannelComposition<*, *>) : VBox(3.0) {

    val enabledProperty: BooleanProperty
        get() = composition.globalRangeProperty

    init {
        val converter = composition.converter
        val min = converter.minProperty(0)
        val max = converter.maxProperty(0)
        val minField = NumberField.doubleField(min.get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
        val maxField = NumberField.doubleField(max.get(), { true }, ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST)
        minField.valueProperty().bindBidirectional(min)
        maxField.valueProperty().bindBidirectional(max)
        minField.textField.tooltip = Tooltip("min")
        maxField.textField.tooltip = Tooltip("max")
        listOf(minField.textField, maxField.textField).forEach { it.disableProperty().bind(enabledProperty.not()) }
        val toggle = HBox(
            10.0,
            Label("Global Intensity Range"),
            ToggleSwitch().apply {
                selectedProperty().bindBidirectional(enabledProperty)
                tooltip = Tooltip("the same min and max for every channel; the toolbar's Auto then estimates one range over the active channels")
            }
        ).apply { alignment = Pos.CENTER_LEFT }
        children.setAll(toggle, minMaxGrid(minField.textField, maxField.textField))

        /* while on, channel 0's range is every channel's range */
        fun apply() {
            if (!enabledProperty.get())
                return
            for (channel in 1 until converter.numChannels()) {
                converter.minProperty(channel).set(min.get())
                converter.maxProperty(channel).set(max.get())
            }
        }
        enabledProperty.subscribe { _, _ -> apply() }
        min.subscribe { _, _ -> apply() }
        max.subscribe { _, _ -> apply() }
    }

    companion object {

        /* min and max side by side under their headers */
        fun minMaxGrid(minField: TextField, maxField: TextField) = GridPane().apply {
            hgap = 5.0
            vgap = 2.0
            add(Label("Min"), 0, 0)
            add(Label("Max"), 1, 0)
            add(minField, 0, 1)
            add(maxField, 1, 1)
            columnConstraints.setAll(List(2) { ColumnConstraints().apply { hgrow = Priority.ALWAYS; percentWidth = 50.0 } })
        }
    }
}
