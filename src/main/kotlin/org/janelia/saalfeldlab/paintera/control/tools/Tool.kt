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
import java.util.concurrent.atomic.AtomicBoolean

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
	protected var subscriptions: Subscription = Subscription.EMPTY
	override val isValidProperty = SimpleBooleanProperty(true)

	override fun activate() {
		activeViewerProperty.bind(mode?.activeViewerProperty ?: paintera.baseView.mostRecentFocusHolder)
		/* this handles installing into the currently active viewer */
		activeViewerProperty.get()?.viewer()?.let { installInto(it) }
		/* This handles viewer changes while  activated */
		val activeViewerSub = activeViewerProperty.subscribe { old, new ->
			old?.viewer()?.let { removeFrom(it) }
			new?.viewer()?.let { installInto(it) }
		}
		val firstTimeOnly = AtomicBoolean(true)
		subscriptions = subscriptions
			.and(activeViewerSub)
			.and {
				/* unsubscribing must be idempotent*/
				if (!firstTimeOnly.getAndSet(false)) {
					LOG.debug { "Tool deactivate ($this) called multiple times" }
					return@and
				}

				if (installedInto.isNotEmpty()) removeFromAll()
				if (activeViewerProperty.isBound) activeViewerProperty.unbind()
				activeViewerProperty.set(null)
			}
	}

	override fun deactivate() {
		subscriptions.unsubscribe()
		subscriptions = Subscription.EMPTY
	}

	override val statusProperty = SimpleStringProperty()

	val activeViewerProperty = SimpleObjectProperty<OrthogonalViews.ViewerAndTransforms?>()

	@Synchronized
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

	@Synchronized
	fun removeFromAll() {
		installedInto.keys.toSet().forEach { node ->
			LOG.debug { "removing $this from all nodes" }
			removeFrom(node)
		}
		installedInto.clear()
	}

	@Synchronized
	fun removeFrom(node: Node) {
		installedInto[node]?.let { actions ->
			LOG.debug { "removing $this from node $node" }
			actions.removeAll { actionSet ->
				node.removeActionSet(actionSet)
				true
			}
			if (actions.isEmpty()) installedInto -= node
		}
	}

	val activeViewerAndTransforms by activeViewerProperty.nullableVal()
	val activeViewer: ViewerPanelFX? by activeViewerProperty.createNullableValueBinding { it?.viewer() }.nullableVal()

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}
