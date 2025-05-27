package org.janelia.saalfeldlab.paintera.control.modes

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableObjectValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.Event
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseEvent
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import javafx.scene.layout.GridPane
import javafx.util.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.installActionSet
import org.janelia.saalfeldlab.fx.actions.ActionSet.Companion.removeActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.createObservableBinding
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.extensions.nullableVal
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews.ViewerAndTransforms
import org.janelia.saalfeldlab.fx.ui.ActionBar
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions
import org.janelia.saalfeldlab.paintera.control.tools.REQUIRES_ACTIVE_VIEWER
import org.janelia.saalfeldlab.paintera.control.tools.Tool
import org.janelia.saalfeldlab.paintera.control.tools.ToolBarItem
import org.janelia.saalfeldlab.paintera.control.tools.ViewerTool
import org.janelia.saalfeldlab.paintera.control.tools.paint.PaintTool
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import java.util.concurrent.LinkedBlockingQueue

interface ControlMode {

	fun enter() {}

	fun exit() {}

	val allowedActions: AllowedActions
	val statusProperty: StringProperty

	companion object {
		internal val keyAndMouseBindings = KeyAndMouseBindings(PainteraBaseKeys.namedCombinationsCopy())
	}

}

interface SourceMode : ControlMode {
	val activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>
	val activeViewerProperty: SimpleObjectProperty<ViewerAndTransforms?>

	override fun exit() {
		activeSourceStateProperty.unbind()
		activeViewerProperty.unbind()
		activeSourceStateProperty.set(null)
		activeViewerProperty.set(null)
	}
}

interface ToolMode : SourceMode {

	private object ToolChange {
		val channel = Channel<Job>()
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

		init {
			scope.launch {
				for (msg in channel) {
					runCatching {
						msg.start()
						msg.join()
					}
				}
			}
		}
	}

	val tools: ObservableList<Tool>

	/**
	 * Actions that will be added to the active viewer when [enter]ing this mode and removed when [exit]ing the mode.
	 * Will move automatically when the active viewer changes.
	 */
	val activeViewerActions: List<ActionSet>

	val defaultTool: Tool?
		get() = NavigationTool

	var activeToolProperty: ObjectProperty<Tool?>
	var activeTool: Tool?

	val modeToolsBar: ActionBar
	val modeActionsBar: ActionBar
	val toolActionsBar: ActionBar

	/**
	 * Subscriptions that should be unsubscribed from when [exit] is called.
	 */
	var subscriptions: Subscription

	override fun enter() {
		/* Keep default tool selected if nothing else */
		val activeToolSubscription = activeToolProperty.subscribe { activeTool ->
			(activeTool ?: defaultTool)?.let { switchTool(it) }
		}
		val activeViewerSubscription = activeViewerProperty.subscribe { old, new ->
			/* remove the actions from old, add to new */
			activeViewerActions.forEach { actionSet ->
				old?.viewer()?.removeActionSet(actionSet)
				new?.viewer()?.installActionSet(actionSet)
			}
		}

		subscriptions = subscriptions
			.and(activeToolSubscription)
			.and(activeViewerSubscription)
		super.enter()
	}

	override fun exit() {
		super.exit()
		subscriptions.unsubscribe()
		subscriptions = Subscription.EMPTY
		runBlocking {
			switchTool(null)?.join()
		}
	}

	fun switchTool(tool: Tool?): Job? {
		if (activeTool == tool)
			return null

		LOG.debug { "Switch from $activeTool to $tool" }


		val switchToolJob = ToolChange.scope.launch(start = CoroutineStart.LAZY) {
			LOG.debug { "Deactivated $activeTool" }
			activeTool?.deactivate()
			(activeTool as? ViewerTool)?.removeFromAll()


			/* If the mode was changed before we can activate, switch to null */
			val activeMode = paintera.baseView.activeModeProperty.value
			activeTool = when {
				activeMode != this@ToolMode -> null // wrong mode
				tool?.isValidProperty?.value == false -> null // tool is not currently valid
				else -> tool?.apply {
					activate()
					LOG.debug { "Activated $activeTool" }
				} // try to activate
			}
		}
		switchToolJob.invokeOnCompletion { cause ->
			when (cause) {
				null -> InvokeOnJavaFXApplicationThread { showToolBars() }
				is CancellationException -> LOG.debug { "Switch to $tool cancelled" }
				else -> LOG.error(cause) { "Switch to $tool failed" }
			}
		}

		ToolChange.scope.launch {
			ToolChange.channel.send(switchToolJob)
		}

		return switchToolJob
	}

	private fun showToolBars(show: Boolean = true) {
		modeToolsBar.show(show)
		modeActionsBar.show(show)
		toolActionsBar.show(show)
	}

	fun createToolBar(): GridPane {
		return GridPane().apply {

			var col = 0

			modeToolsBar.set(*tools.filterIsInstance<ToolBarItem>().toTypedArray())
			addColumn(col++, modeToolsBar)

			modeActionsBar.set(*activeViewerActions.toTypedArray())
			addColumn(col++, modeActionsBar)

			addColumn(col, toolActionsBar)


			modeToolsBar.toggleGroup?.apply {
				/* When the selected tool toggle changes, switch to that tool (if we aren't already) or default if unselected only */
				selectedToggleProperty().addListener { _, _, selected ->
					selected?.let {
						(it.userData as? Tool)?.let { tool ->
							if (activeTool != tool) {
								if ((it.properties.getOrDefault(REQUIRES_ACTIVE_VIEWER, false) as? Boolean) == true) {
									selectViewerBefore {
										switchTool(tool)
									}
								} else {
									switchTool(tool)
								}
								//TODO this should be refactored and more generic
								(tool as? PaintTool)?.enteredWithoutKeyTrigger = true
							}
						}
					} ?: switchTool(defaultTool)
				}

				/* when the active tool changes, update the toggle to reflect the active tool */
				activeToolProperty.subscribe { tool ->
					tool?.let { newTool ->
						toggles
							.firstOrNull { it.userData == newTool }
							?.also { toggleForTool -> selectToggle(toggleForTool) }
						val toolActionSets = newTool.actionSets.toTypedArray()
						InvokeOnJavaFXApplicationThread { toolActionsBar.set(*toolActionSets) }
					}
				}
			}
		}

	}

	/**
	 * Prompt the user to select a viewer prior to executing [afterViewerIsSelected].
	 * User can abandon the selection with [KeyCode.ESCAPE].
	 *
	 * @param afterViewerIsSelected will be executed if a viewer is clicked, and [KeyCode.ESCAPE] is not pressed
	 */
	fun selectViewerBefore(afterViewerIsSelected: () -> Unit) {
		/* Ensure no active tools when prompting to select viewer*/
		runBlocking { switchTool(null)?.join() }
		/* To ensure it's done, */
		/* temporarily revoke permissions, so no actions are performed until we select a viewer  */
		paintera.baseView.allowedActionsProperty().suspendPermisssions()

		statusProperty.unbind()
		this.statusProperty.set("Select a Viewer...")


		val cleanup = SimpleBooleanProperty(false)

		paintera.baseView.orthogonalViews().views().forEach { view ->
			with(view) {
				/* defined later, but declared here for usage inside filters */
				lateinit var resetFilterAndPermissions: () -> Unit

				/* store the prev cursor, change to CROSSHAIR  */
				val prevCursor = cursor

				/* select the viewer, and trigger the callback */
				val selectViewEvent = EventHandler<MouseEvent> {
					it.consume()
					paintera.baseView.currentFocusHolder.get()?.also {
						/* trigger callback */
						afterViewerIsSelected()
						/* then indicate cleanup  */
						cleanup.set(true)
					}
				}

				/* In case ESC is pressed to cancel the selection, switch to the default tool  */
				val escapeFilter = EventHandler<KeyEvent> {
					if (it.code == KeyCode.ESCAPE) {
						cleanup.set(true)
						switchTool(defaultTool)
					}
				}


				val modeChangeListener = ChangeListener<ControlMode> { _, _, _ -> cleanup.set(true) }
				val toolChangeListener = ChangeListener<Tool?> { _, _, _ -> cleanup.set(true) }

				paintera.baseView.activeModeProperty.addListener(modeChangeListener)
				activeToolProperty.addListener(toolChangeListener)

				resetFilterAndPermissions = {
					paintera.baseView.activeModeProperty.removeListener(modeChangeListener)
					activeToolProperty.removeListener(toolChangeListener)
					removeEventFilter(MOUSE_CLICKED, selectViewEvent)
					removeEventFilter(KEY_PRESSED, escapeFilter)
					paintera.baseView.allowedActionsProperty().restorePermisssions()
					if (cursor == Cursor.CROSSHAIR) {
						cursor = prevCursor
					}
				}

				cleanup.addListener { _, _, cleanup ->
					if (cleanup) resetFilterAndPermissions()
				}

				/* add filters, and update the cursor to indicate selection */
				cursor = Cursor.CROSSHAIR


				/* listen for the selection; block all mouse click events temporarily, so no other filters are called  */
				addEventFilter(MOUSE_CLICKED, selectViewEvent)

				/* listen for ESC if we wish to cancel*/
				addEventFilter(KEY_PRESSED, escapeFilter)
			}
		}
	}


	fun <T : Event> Node.waitForEvent(event: EventType<T>): T? {
		/* temporarily revoke permissions, so no actions are performed until we receive event [T]   */
		paintera.baseView.allowedActionsProperty().suspendPermisssions()

		val queue = LinkedBlockingQueue<Any>()

		InvokeOnJavaFXApplicationThread {
			lateinit var escapeFilter: EventHandler<KeyEvent>
			lateinit var waitForEventFilter: EventHandler<T>

			val resetFilterAndPermissions = { it: T? ->
				removeEventFilter(event, waitForEventFilter)
				removeEventFilter(KEY_PRESSED, escapeFilter)
				paintera.baseView.allowedActionsProperty().restorePermisssions()
				queue.offer(it ?: Any())
			}

			waitForEventFilter = EventHandler<T> {
				it.consume()
				resetFilterAndPermissions(it)
			}

			/* In case ESC is pressed stop waiting  */
			escapeFilter = EventHandler<KeyEvent> {
				resetFilterAndPermissions(null)
			}

			addEventFilter(event, waitForEventFilter)
			addEventFilter(KEY_PRESSED, escapeFilter)
		}

		return (queue.take() as? T)
	}

	fun disableUnfocusedViewers() {
		val orthoViews = paintera.baseView.orthogonalViews()
		activeViewerProperty.get()?.viewer() ?: return
		orthoViews.views()
			.stream()
			.filter { activeViewerProperty.get()?.viewer()!! != it }
			.forEach { orthoViews.disableView(it) }
	}

	fun enableAllViewers() {
		val orthoViews = paintera.baseView.orthogonalViews()
		orthoViews.views().forEach { orthoViews.enableView(it) }
	}

	companion object {
		private val LOG = KotlinLogging.logger { }
	}
}

abstract class AbstractSourceMode : SourceMode {
	final override val activeSourceStateProperty = SimpleObjectProperty<SourceState<*, *>?>()
	final override val activeViewerProperty = SimpleObjectProperty<ViewerAndTransforms?>()

	private val currentStateObservable: ObservableObjectValue<SourceState<*, *>?>
		get() = paintera.baseView.sourceInfo().currentState() as ObservableObjectValue<SourceState<*, *>?>
	private val currentViewerObservable
		get() = paintera.baseView.currentFocusHolder

	private val keyBindingsProperty = activeSourceStateProperty.createNullableValueBinding {
		it?.let { paintera.baseView.keyAndMouseBindings.getConfigFor(it).keyCombinations }
	}
	protected val keyBindings by keyBindingsProperty.nullableVal()

	private var sourceAndViewerSub = subscribeToSourceActions()

	/* This will add and remove the state specific actions from:
	 *  - the correct viewers when the active viewer changes
	 *  - the global action set when the active source changes */
	/* This will add and remove the state specific actions from the correct viewers when the active viewer changes */
	private fun subscribeToSourceActions(): Subscription = activeViewerProperty.createObservableBinding(activeSourceStateProperty) {
		activeViewerProperty.get() to activeSourceStateProperty.get()
	}.subscribe { (prevViewer, prevSource), (viewer, source) ->
		/* if the source changed, handle the global actions*/
		if (prevSource != source) {
			prevSource?.apply { paintera.defaultHandlers.globalActionHandlers.removeAll(globalActionSets) }
			source?.apply { paintera.defaultHandlers.globalActionHandlers.addAll(globalActionSets) }
		}

		/* if the viewer changed ( or the source), remove the old ones */
		if (prevViewer != viewer || prevSource != source) {
			prevViewer?.let { viewer ->
				prevSource?.let { source ->
					source.viewerActionSets.forEach { actionSet ->
						viewer.viewer().removeActionSet(actionSet)
					}
				}
			}

			/* if we have a new viewer, install the actions */
			viewer?.let { viewer ->
				source?.let { source ->
					source.viewerActionSets.forEach { actionSet ->
						viewer.viewer().installActionSet(actionSet)
					}
				}
			}
		}

	}

	override fun enter() {
		sourceAndViewerSub = subscribeToSourceActions()
		activeSourceStateProperty.bind(currentStateObservable)
		activeViewerProperty.bind(currentViewerObservable)
		super.enter()
	}

	override fun exit() {
		super.exit()
		activeSourceStateProperty.unbind()
		activeViewerProperty.unbind()
		activeSourceStateProperty.set(null)
		activeViewerProperty.set(null)
		sourceAndViewerSub.unsubscribe()
	}
}


/**
 * Abstract tool mode
 *
 * @constructor Create empty Abstract tool mode
 */
abstract class AbstractToolMode : AbstractSourceMode(), ToolMode {

	override val tools: ObservableList<Tool> = FXCollections.observableArrayList()
	final override var activeToolProperty: ObjectProperty<Tool?> = SimpleObjectProperty<Tool?>()
	final override var activeTool by activeToolProperty.nullable()

	override val modeActionsBar: ActionBar = ActionBar()
	override val modeToolsBar: ActionBar = ActionBar()
	override val toolActionsBar: ActionBar = ActionBar()

	override var subscriptions: Subscription = Subscription.EMPTY

	override val statusProperty: StringProperty = SimpleStringProperty()
	protected fun escapeToDefault() = painteraActionSet("escape to default") {
		KEY_PRESSED(KeyCode.ESCAPE) {
			/* Don't change to default if we are default */
			verify("Default Tool Is Not Already Active") { activeTool != null && activeTool != defaultTool }
			onAction {
				switchTool(defaultTool)
			}
		}
	}

	override fun enter() {
		super<ToolMode>.enter()
		super<AbstractSourceMode>.enter()
		var toolStatusSub: Subscription? = null
		val activeToolStatusSub = activeToolProperty.subscribe { tool ->
			toolStatusSub?.unsubscribe()
			toolStatusSub = tool?.statusProperty?.subscribe { toolStatus ->
				InvokeOnJavaFXApplicationThread {
					this@AbstractToolMode.statusProperty.set(toolStatus)
				}
			}
		}
		subscriptions = subscriptions.and(activeToolStatusSub)
	}

	override fun exit() {
		super<AbstractSourceMode>.exit()
		super<ToolMode>.exit()
	}
}


