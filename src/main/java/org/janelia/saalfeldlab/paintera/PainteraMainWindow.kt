package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.Properties2
import java.util.function.BiConsumer

typealias PropertiesListener = BiConsumer<Properties2?, Properties2?>

class PainteraMainWindow {

    val baseView = PainteraBaseView(
            PainteraBaseView.reasonableNumFetcherThreads(),
            ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()))

    val gridConstraintsManager = GridConstraintsManager()

    val paneWithStatus = BorderPaneWithStatusBars(baseView)

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    val defaultHandlers = PainteraDefaultHandlers(
            baseView,
            keyTracker,
            mouseTracker,
            paneWithStatus,
            { projectDirectory.actualDirectory.absolutePath },
            gridConstraintsManager)

	var properties: Properties2? = null
	set(properties) {
		field = properties
		propertiesListeners.forEach { it.accept(properties, field) }
	}

	private val propertiesListeners = mutableListOf<PropertiesListener>()

	fun addPropertiesListener(listener: (Properties2?, Properties2?) -> Unit) = addPropertiesListener(PropertiesListener(listener))

	fun addPropertiesListener(listener: PropertiesListener) {
		propertiesListeners.add(listener)
		listener.accept(null, properties)
	}

	// TODO here stuf
    init {
        baseView.orthogonalViews().grid().manage(gridConstraintsManager)
		addPropertiesListener(( { old, new ->
			old?.let {
			}
			new?.let {
				paneWithStatus.arbitraryMeshConfigNode().config.bindTo(it.arbitraryMeshConfig)
				paneWithStatus.bookmarkConfigNode().bookmarkConfig
			}
		}))
    }

}
