package org.janelia.saalfeldlab.paintera.control.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.*
import javafx.util.Subscription
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.Action
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.NamedKeyBinding
import org.janelia.saalfeldlab.fx.event.KeyTracker
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

	val graphic: () -> Node?
		get() = { null }

	val name: String
	val keyTrigger: NamedKeyBinding?
	val action: Action<*>?
		get() = null

	val toolBarButton: ButtonBase
		get() {
			val node = graphic()
			val button = action?.let { action ->
				var toggleGroup: ToggleGroup? = null
				node?.also { graphic ->
					toggleGroup = graphic.properties["TOGGLE_GROUP"] as? ToggleGroup
				}

				val btn = toggleGroup?.let { group ->
					ToggleButton(null, node).also { it.toggleGroup = group }
				} ?: Button(null, node)

				btn.apply {
					onAction = EventHandler {
						action(null)
					}
				}
			} ?: ToggleButton(null, node)

			return button.also { btn ->
				btn.id = name
				//FIXME Caleb: this is either not necessary, or magic. Regardless, should fix it
				//  why conditionally bind isDisabled only if a graphic?
				btn.graphic?.let {

					val actionIsValid = { action?.isValid(null) ?: true }

					/* Listen on disabled when visible*/
					if ("ignore-disable" !in it.styleClass) {
						paintera.baseView.isDisabledProperty.`when`(btn.visibleProperty()).subscribe { disabled ->
							btn.disableProperty().set(disabled || !actionIsValid())
						}
					} else {
						btn.disableProperty().set(!actionIsValid())
					}
					/* set initial state to */
					btn.disableProperty().set(!actionIsValid())

					(this as? Tool)?.isValidProperty?.`when`(btn.visibleProperty())?.subscribe { isValid -> btn.disableProperty().set(!isValid) }
				}
				btn.styleClass += "toolbar-button"
				btn.tooltip = Tooltip(
					keyTrigger?.let { trigger ->
						"$name: ${KeyTracker.keysToString(*trigger.keyCodes.toTypedArray())}"
					} ?: name
				)
			}
		}
}

const val REQUIRES_ACTIVE_VIEWER = "REQUIRES_ACTIVE_VIEWER"

abstract class ViewerTool(protected val mode: ToolMode? = null) : Tool, ToolBarItem {

	private val installedInto: MutableMap<Node, MutableList<ActionSet>> = ConcurrentHashMap()
	protected var subscriptions: Subscription = Subscription.EMPTY
	override val isValidProperty = SimpleBooleanProperty(true)

	override fun activate() {
		activeViewerProperty.bind(mode?.activeViewerProperty ?: paintera.baseView.lastFocusHolder)
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
