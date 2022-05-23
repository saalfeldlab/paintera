package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import org.janelia.saalfeldlab.paintera.Version
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.paintera.ui.RefreshButton
import org.slf4j.LoggerFactory

internal object ReadMeDialog {


    private val LOG = LoggerFactory.getLogger(this::class.java)
    private val README_URL = getReadmeUrl()

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
