package org.janelia.saalfeldlab.paintera.ui

import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.control.TitledPane
import javafx.scene.layout.VBox
import javafx.util.Subscription
import org.janelia.saalfeldlab.paintera.control.modes.ModeSetting
import org.janelia.saalfeldlab.paintera.paintera
import java.util.IdentityHashMap

internal class ModeSettingsPane : TitledPane("Mode Settings", VBox(10.0)) {

	private val sections = content as VBox

	private val panes = IdentityHashMap<ModeSetting, TitledPane>()
	private var modeSubscription: Subscription? = null

	init {
		isExpanded = false
		sections.padding = Insets(5.0)
		paintera.baseView.activeModeProperty.subscribe { mode ->
			modeSubscription?.unsubscribe()
			val settings = mode?.modeSettings
			modeSubscription = settings?.let { list ->
				val listener = ListChangeListener<ModeSetting> { rebuild(list) }
				list.addListener(listener)
				Subscription { list.removeListener(listener) }
			}
			rebuild(settings.orEmpty())
		}
	}

	private fun rebuild(settings: List<ModeSetting>) {
		isCollapsible = settings.isNotEmpty()
		if (settings.isEmpty()) {
			isExpanded = false
			content = null
			sections.children.clear()
			return
		}
		content = sections
		sections.children.setAll(settings.map { setting ->
			panes.getOrPut(setting) { TitledPane(setting.title, setting.node).apply { isExpanded = false } }
		})
	}
}
