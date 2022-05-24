package org.janelia.saalfeldlab.paintera.control.tools

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.scene.Cursor
import javafx.scene.Node
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.installTool
import org.janelia.saalfeldlab.fx.actions.removeTool
import org.janelia.saalfeldlab.fx.extensions.createValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.PainteraDefaultHandlers.Companion.currentFocusHolder
import org.janelia.saalfeldlab.paintera.paintera
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

interface Tool {

    fun activate() {}
    fun deactivate() {}

    val statusProperty: StringProperty
    val cursorProperty: SimpleObjectProperty<Cursor>
        get() = SimpleObjectProperty(Cursor.DEFAULT)
    val graphicProperty: SimpleObjectProperty<Node>
    /* each action could have a:
    *   - cursor
    *   - graphic
    *   - shortcut */

    val actionSets: List<ActionSet>
}

abstract class ViewerTool : Tool {

    private val installedInto: MutableSet<Node> = mutableSetOf()

    override fun activate() {
        activeViewerProperty.bind(paintera.baseView.orthogonalViews().currentFocusHolder())
    }

    override fun deactivate() {
        activeViewerAndTransforms?.viewer()?.let { removeFrom(it) }
        activeViewerProperty.unbind()
        activeViewerProperty.set(null)
    }

    override val statusProperty = SimpleStringProperty()
    override val graphicProperty: SimpleObjectProperty<Node>
        get() = TODO("Not yet implemented")

    val activeViewerProperty = SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>().apply {
        addListener { _, old, new ->
            if (old != new) {
                old?.viewer()?.let { removeFrom(it) }
                new?.viewer()?.let { installInto(it) }
            }
        }
    }

    fun installInto(node: Node) {
        if (!installedInto.contains(node)) {
            node.installTool(this)
            installedInto += node
        } else {
            LOG.debug("Tool (${this.javaClass.simpleName}) already installed into node ($node)")
        }
    }

    fun removeFrom(node: Node) {
        if (installedInto.contains(node)) {
            node.removeTool(this)
            installedInto.remove(node)
        }
    }

    val activeViewerAndTransforms by activeViewerProperty.nullableVal()
    val activeViewer by activeViewerProperty.createValueBinding { it?.viewer() }.nullableVal()

    companion object {
        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    }
}

