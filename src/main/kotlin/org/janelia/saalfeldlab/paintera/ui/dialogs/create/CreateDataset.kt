package org.janelia.saalfeldlab.paintera.ui.dialogs.create

import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.scene.input.KeyEvent.KEY_PRESSED
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.fx.ui.Exceptions
import org.janelia.saalfeldlab.paintera.Constants
import org.janelia.saalfeldlab.paintera.PainteraBaseKeys
import org.janelia.saalfeldlab.paintera.control.actions.MenuAction
import org.janelia.saalfeldlab.paintera.control.actions.MenuActionType
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.label.ConnectomicsLabelState
import org.janelia.saalfeldlab.paintera.state.label.n5.N5BackendLabel
import org.janelia.saalfeldlab.paintera.state.metadata.MetadataState
import org.janelia.saalfeldlab.paintera.ui.dialogs.PainteraAlerts.initAppDialog
import kotlin.jvm.optionals.getOrNull

private val LOG = KotlinLogging.logger {}

/**
 * Create a new 3D+ label dataset and add it as a source.
 */
object CreateDataset : MenuAction("_Label Source...") {

    init {
        verifyPermission(MenuActionType.CreateLabelSource)
        onActionWithState<CreateDatasetActionState> {
            try {
                getDialog().showAndWait().getOrNull() ?: return@onActionWithState
                val metadataState = create() ?: return@onActionWithState
                addLabelSource(metadataState, name)
            } catch (e: Exception) {
                LOG.warn(e) { "Unable to create new label dataset" }
                Exceptions.exceptionAlert(Constants.NAME, "Unable to create new label dataset: ${e.message}", e).apply {
                    initAppDialog()
                    show()
                }
            }
        }
    }

    fun actionSet() = painteraActionSet("Create new label dataset", MenuActionType.CreateLabelSource) {
        KEY_PRESSED(PainteraBaseKeys.namedCombinationsCopy(), PainteraBaseKeys.CREATE_NEW_LABEL_DATASET) {
            onAction { CreateDataset() }
        }
    }

    private fun addLabelSource(metadataState: MetadataState, name: String) {
        val baseView = paintera.baseView
        /* the type bounds are dictated by the metadataState, so no need to specify them */
        val backend = N5BackendLabel.createFrom<Nothing, Nothing>(metadataState, baseView.propagationQueue)
        val viewer3D = baseView.viewer3D()
        baseView.addState(
            ConnectomicsLabelState(
                backend,
                viewer3D.meshesGroup,
                viewer3D.viewFrustumProperty,
                viewer3D.eyeToWorldTransformProperty,
                baseView.meshWorkerExecutorService,
                baseView.queue,
                0,
                name,
                null
            )
        )
    }
}
