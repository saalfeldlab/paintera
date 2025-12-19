package org.janelia.saalfeldlab.paintera.util.debug

import javafx.scene.control.MenuItem
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Window
import javafx.util.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.janelia.saalfeldlab.fx.actions.Action.Companion.installAction
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.control.actions.ActionMenu
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.ui.menus.menuBar
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.*
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal class DebugMenu(vararg menus: MenuItem) : ActionMenu("_Debug", null, *menus) {
	init {
		isVisible = Paintera.debugMode
	}

	companion object {
		init {
			DebugModeProperty.subscribe { debug ->
				if (debug) {
					val debugMenu = DebugMenu(*DebugMenuItems.entries.map { it.menuAction.menuItem }.toTypedArray())
					for (menuItem in DebugMenuItems.entries) {
						menuItem.registerActionToWindows().also {
							DebugModeProperty.trackDebugSubscription(it)
						}
					}
					menuBar.menus.add(debugMenu)
				}
			}
		}
	}
}

private enum class DebugMenuItems(val menuAction: MenuAction, val initOnStart: Boolean = false) {
	ReloadCss(ReloadCssAction()),
	LiveReloadCss(LiveReloadCssAction()),
	DebugWindow(DebugWindowAction());

	init {
		if (initOnStart) {
			Paintera.whenPaintable {
				registerActionToWindows()
			}
		}
	}

	private fun registerActionToWindow(window: Window): Subscription {
		var subscription = Subscription.EMPTY
		window.sceneProperty().subscribe { scene ->
			subscription.unsubscribe()
			subscription = scene.installAction(menuAction)
		}
		DebugModeProperty.trackDebugSubscription { subscription.unsubscribe() }
		return subscription
	}

	fun registerActionToWindows(): Subscription = DebugModeProperty.subscribe { debugMode ->
		var windowSubscription = Subscription.EMPTY
		if (debugMode) {
			Window.getWindows().subscribe {
				windowSubscription.unsubscribe()
				Window.getWindows().forEach {
					windowSubscription = registerActionToWindow(it) + windowSubscription
				}
			}
		}
	}
}

internal open class ReloadCssAction(text : String = "Reload CSS") : MenuAction(text, KeyEvent.KEY_TYPED) {

	companion object {

		private fun styleSheetIsURL(string: String): Boolean = runCatching { URI.create(string).toURL() }.map { true }.getOrElse { false }

		fun mapStyleSheet(styleSheet: String): String {
			val localStyleSheet = styleSheet
				.takeUnless { styleSheetIsURL(it) }
				?.takeIf { it.startsWith("/") }
				?: "/$styleSheet"
			return Paintera::class.java.getResource(localStyleSheet)
				?.toExternalForm()
				?.replace("target/classes", "src/main/resources")
				?.let { uri ->
					runCatching {
						Paths.get(URI.create(uri)).takeIf { it.exists() }?.toUri().toString()
					}.getOrNull()
				} ?: styleSheet
		}

		fun reloadCss() {
			InvokeOnJavaFXApplicationThread {
				Window.getWindows().forEach { window ->
					val styleSheets = window.scene.stylesheets.toList().mapNotNull { css -> mapStyleSheet(css) }
					window.scene.stylesheets.clear()
					window.scene.stylesheets.setAll(styleSheets)
					window.scene.root.requestLayout()
				}
			}
		}
	}

	init {
		keyTracker = { paintera.keyTracker }
		filter = true
		consume = false
		keysDown = listOf(KeyCode.BACK_SLASH, KeyCode.R)
		verify("Debug Mode is Active") { DebugModeProperty.get() }
		onAction { reloadCss() }
	}
}

internal class LiveReloadCssAction : ReloadCssAction("Live Reload CSS") {

	private val watcher by lazy { FileSystems.getDefault().newWatchService() }

	private val watchLoop by lazy {
		CoroutineScope(Dispatchers.IO).launch {
			while (DebugModeProperty.get()) {
				watcher.poll()?.let { key ->
					key.pollEvents()?.forEach { event ->
						InvokeOnJavaFXApplicationThread {
							Window.getWindows().forEach { window ->
								val copy = window.scene.stylesheets.toList()
								window.scene.stylesheets.clear()
								window.scene.stylesheets.addAll(copy)
							}
						}
					} ?: delay(100)
					key.reset()
				}
			}
		}
	}

	var watchSubscriptions : Subscription? = Subscription { watchLoop.cancel() }

	fun initWatchAndReloadCss() {
		InvokeOnJavaFXApplicationThread {
			watchSubscriptions?.unsubscribe()
			watchSubscriptions = null
			Window.getWindows().forEach { window ->
				val styleSheets = window.scene.stylesheets.toList().mapNotNull { css -> mapStyleSheet(css) }
				window.scene.stylesheets.clear()
				window.scene.stylesheets.setAll(styleSheets)

				styleSheets.forEach { styleSheet ->
					val sheetUri = runCatching {
						Paths.get(URI.create(styleSheet)).takeIf { it.exists() }?.toUri().toString()
					}.getOrElse {
						it.printStackTrace()
						null
					}
					watchSubscriptions += sheetUri?.watch()
				}
			}
		}
	}

	fun String.watch() : Subscription {
		val path = Paths.get(URI.create(this)).let {
			it.takeIf { it.isDirectory() } ?: it.parent
		}
		val key = path.register(watcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, )
		return Subscription { key.cancel() }
	}

	init {
		keysDown = listOf(KeyCode.SHIFT, KeyCode.BACK_SLASH, KeyCode.R)
	}
}