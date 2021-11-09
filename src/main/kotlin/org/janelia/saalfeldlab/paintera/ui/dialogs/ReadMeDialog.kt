package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import javafx.stage.Modality
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.paintera.Version
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfigNode
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.properties
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.slf4j.LoggerFactory

internal object ReadMeDialog {

    private const val BUTTON_TEXT = "_README"
    private const val TOOLTIP_TEXT = "Open README.md"

    private val LOG = LoggerFactory.getLogger(this::class.java)
    private val README_URL = getReadmeUrl()

    val button = Buttons.withTooltip(BUTTON_TEXT, TOOLTIP_TEXT) { showReadme() }
    val keyBindingsDialog = KeyAndMouseConfigNode(properties.keyAndMouseConfig, paintera.baseView.sourceInfo()).node
    val keyBindingsPane = TitledPane("Key Bindings", keyBindingsDialog)
    val dialog: Alert by lazy {
        PainteraAlerts.information("_Close", true).apply {
            initModality(Modality.NONE)
            dialogPane.content = VBox(keyBindingsPane, button)
            graphic = null
            headerText = null
            dialogPane.minWidth = 1000.0
        }
    }


    fun show() {
        dialog.show()
    }

    private fun getReadmeUrl(): String {
        val version = Version.VERSION_STRING
        val tag = if (version.endsWith("SNAPSHOT"))
            "master"
        else
            "^.*-SNAPSHOT-([A-Za-z0-9]+)$"
                .toRegex()
                .find(version)
                ?.let { it.groupValues[1] }
                ?: "paintera-$version"
        return "https://github.com/saalfeldlab/paintera/blob/$tag/README.md"
    }

    internal fun showReadme() {
        this::class.java.getResource("/README.html")?.toExternalForm()?.let { res ->
            PainteraAlerts.information("_Close", true).apply {
                /* Only load if the button is clicked */
                val webview by lazy {
                    WebView().also {
                        it.engine.load(res)
                        it.maxHeight = Double.POSITIVE_INFINITY
                        VBox.setVgrow(it, Priority.ALWAYS)
                    }
                }
                dialogPane.content = VBox(
                    HBox(
                        TextField(README_URL).also {
                            HBox.setHgrow(it, Priority.ALWAYS)
                            it.tooltip = Tooltip(README_URL)
                            it.isEditable = false
                        },
                        Button(null, RefreshButton.createFontAwesome(2.0)).apply {
                            onAction = EventHandler { webview.engine.load(res) }
                        }),
                    webview
                )
                graphic = null
                headerText = null
                show()
            }
        } ?: LOG.info("Resource '/README.html' not available")
    }
}
