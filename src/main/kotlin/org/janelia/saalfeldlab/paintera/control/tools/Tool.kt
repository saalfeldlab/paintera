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
import org.janelia.saalfeldlab.paintera.control.modes.NavigationTool.actionSets
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.paintera
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
						action()
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
	private var subscriptions: Subscription? = null
	override val isValidProperty = SimpleBooleanProperty(true)

	private class SubImp(sub : Subscription) : Subscription by sub {

		val id = count.getAndIncrement()

		override fun toString(): String {
			return super.toString() + "_$id"
		}

		companion object {
			private var count = AtomicInteger(1)
		}
	}

	override fun activate() {
		println("Activate: $this")
		activeViewerProperty.bind(mode?.activeViewerProperty ?: paintera.baseView.lastFocusHolder)
		/* this handles installing into the currently active viewer */
		activeViewerProperty.get()?.viewer()?.let { installInto(it) }
		/* This handles viewer changes while  activated */
		if (subscriptions != null)
			println("?")
		subscriptions = SubImp(activeViewerProperty.subscribe { old, new ->
			old?.viewer()?.let { removeFrom(it) }
			new?.viewer()?.let { installInto(it) }
		})
		val t = subscriptions
	}

	override fun deactivate() {
		println("Deactivate: $this")
		subscriptions?.let {
			it.unsubscribe()
			subscriptions = null
		}
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
