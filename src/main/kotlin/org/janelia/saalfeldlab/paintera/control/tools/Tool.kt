package org.janelia.saalfeldlab.paintera.control.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.scene.Node
import javafx.scene.control.Labeled
import javafx.scene.control.ToggleButton
import javafx.util.Subscription
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.NamedKeyBinding
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.paintera
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

interface Tool {

	fun activate() {}
	fun deactivate() {}

	val statusProperty: StringProperty
	val isValidProperty: BooleanProperty
	val actionSets: MutableList<ActionSet>
}

interface ToolBarItem {

	val name: String
	val keyTrigger: NamedKeyBinding?
	val action: Action<*>?
		get() = null

	/**
	 * Create a new [Labeled] instance that can be added to the UI to trigger this tool bar item.
	 *
	 * @return
	 */
	fun newToolBarControl() : Labeled = ToggleButton()
}

const val REQUIRES_ACTIVE_VIEWER = "REQUIRES_ACTIVE_VIEWER"

abstract class ViewerTool(protected val mode: ToolMode? = null) : Tool, ToolBarItem {

	private val installedInto: MutableMap<Node, MutableList<ActionSet>> = ConcurrentHashMap()
	private var subscriptions: Subscription = Subscription.EMPTY
	override val isValidProperty = SimpleBooleanProperty(true)

	override fun activate() {
		activeViewerProperty.bind(mode?.activeViewerProperty ?: paintera.baseView.mostRecentFocusHolder)
		/* this handles installing into the currently active viewer */
		activeViewerProperty.get()?.viewer()?.let { installInto(it) }
		/* This handles viewer changes while  activated */
		subscriptions.unsubscribe()
		subscriptions = activeViewerProperty.subscribe { old, new ->
			old?.viewer()?.let { removeFrom(it) }
			new?.viewer()?.let { installInto(it) }
		}
	}

	override fun deactivate() {
		subscriptions.unsubscribe()
		subscriptions = Subscription.EMPTY
		removeFromAll()
		activeViewerProperty.unbind()
		activeViewerProperty.set(null)
	}

	override val statusProperty = SimpleStringProperty()

	val activeViewerProperty = SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>()

	fun installInto(node: Node) {
		installedInto.computeIfAbsent(node) {
			LOG.debug { "installing $this" }
			actionSets.map { actionSet ->
				node.installActionSet(actionSet)
				actionSet
			}
				.toMutableList()
				.let { Collections.synchronizedList(it) }
		}
	}

	fun removeFromAll() {
		installedInto.keys.toSet().forEach { node ->
			LOG.debug { "removing $this from all nodes" }
			removeFrom(node)
		}
		synchronized(this) {
			installedInto.clear()
		}
	}

	fun removeFrom(node: Node) {
		installedInto[node]?.let { actions ->
			LOG.debug { "removing $this from node $node" }
			actions.removeIf { actionSet ->
				node.removeActionSet(actionSet)
				true
			}
			if (actions.isEmpty())
				installedInto -= node
		}
	}

	val activeViewerAndTransforms by activeViewerProperty.nullableVal()
	val activeViewer: ViewerPanelFX? by activeViewerProperty.createNullableValueBinding { it?.viewer() }.nullableVal()

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}
