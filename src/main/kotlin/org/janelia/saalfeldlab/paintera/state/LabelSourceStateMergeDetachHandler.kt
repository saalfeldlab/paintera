package org.janelia.saalfeldlab.paintera.state

import bdv.viewer.Interpolation
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent.MOUSE_CLICKED
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.realtransform.RealViews
import net.imglib2.type.label.Label
import net.imglib2.type.numeric.IntegerType
import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.actions.verifyPermission
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.actions.LabelActionType
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignment
import org.janelia.saalfeldlab.paintera.control.selection.SelectedIds
import org.janelia.saalfeldlab.paintera.control.undo.HasHistory
import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import kotlin.jvm.optionals.getOrNull

class LabelSourceStateMergeDetachHandler(
	private val source: DataSource<out IntegerType<*>, *>,
	private val selectedIds: SelectedIds,
	private val assignment: FragmentSegmentAssignment,
	private val idService: IdService
) {

	fun makeActionSets(activeViewer: () -> ViewerPanelFX?): List<ActionSet> {

		val mergeFragments = painteraActionSet("MergeFragments", LabelActionType.Merge) {
			MOUSE_CLICKED(MouseButton.PRIMARY, withKeysDown = arrayOf(KeyCode.SHIFT), keysExclusive = true) {
				verify { activeViewer() != null }
				onAction { activeViewer()?.let { mergeFragments(it) } }
			}
			KEY_PRESSED(LabelSourceStateKeys.FRAG_SEG_ASSIGNMENT_MERGE_SELECTED) {
				onAction { FragmentSegmentAssignment.mergeAllSelected(assignment, selectedIds, idService) }
			}
		}
		val detachFragments = painteraActionSet("DetachFragment", LabelActionType.Split) {
			MOUSE_CLICKED(MouseButton.SECONDARY, withKeysDown = arrayOf(KeyCode.SHIFT), keysExclusive = true) {
				verify { activeViewer() != null }
				onAction { activeViewer()?.let { detachFragment(it) } }
			}
		}

		val assignmentHistory = (assignment as? HasHistory<*>)?.history ?: return listOf(mergeFragments, detachFragments)

		val undoRedoAssignments = painteraActionSet("UndoRedoAssignments") {
		    /* undo/redo can revert either kind of action, so both permissions are required */
			verifyPermission(LabelActionType.Split, LabelActionType.Merge)

			KEY_PRESSED(LabelSourceStateKeys.FRAG_SEG_ASSIGNMENT_UNDO) {
				verify("assignmentHistory canUndo") { assignmentHistory.canUndo.get() }
				onAction { assignmentHistory.undo() }
			}
			KEY_PRESSED(LabelSourceStateKeys.FRAG_SEG_ASSIGNMENT_REDO) {
				verify("assignmentHistory canRedo") { assignmentHistory.canRedo.get() }
				onAction { assignmentHistory.redo() }
			}
		}

		return listOf(mergeFragments, detachFragments, undoRedoAssignments)
	}

	private fun mergeFragments(viewer: ViewerPanelFX) {

		synchronized(viewer) {
			val lastSelection = selectedIds.lastSelection.takeUnless { it == Label.INVALID } ?: return
			val id = viewer.idAtMouseCoordinates().takeUnless { !Label.isForeground(it) } ?: return

			LOG.debug { "Merging fragments: $id -- last selection: $lastSelection" }
			assignment.getMergeAction(id, lastSelection) { idService.next() }
				.getOrNull()
				?.let { assignment.apply(it) }
		}
	}

	private fun detachFragment(viewer: ViewerPanelFX) {

        synchronized(viewer) {
            val lastSelection = selectedIds.lastSelection.takeUnless { it == Label.INVALID } ?: return
            val labelId = viewer.idAtMouseCoordinates().takeUnless { !Label.isForeground(it) } ?: return
            val detachAction = assignment.getDetachAction(labelId, lastSelection).getOrNull() ?: return

            val previousSegment = assignment.getSegment(lastSelection)
            if (labelId == lastSelection && previousSegment != labelId && previousSegment != Label.INVALID) {
                /* Special case where we detach the current active fragment from its own segment.
                 * In that case, we want the previous segment to still be active. */
                selectedIds.activateAlso(previousSegment)
            }
            assignment.apply(detachAction)
        }
	}

	private fun ViewerPanelFX.idAtMouseCoordinates(): Long {
		val screenScaleTransform = AffineTransform3D().also {
            renderUnit.getScreenScaleTransform(0, it)
        }
		val level = state.getBestMipMapLevel(screenScaleTransform, source)

		val sourceToGlobalTransform = AffineTransform3D().also {
            source.getSourceTransform(0, level, it)
        }
		val interpolated = source.getInterpolatedDataSource(0, level, Interpolation.NEARESTNEIGHBOR)
		val sourceAccessInGlobalSpace = RealViews.transformReal(interpolated, sourceToGlobalTransform).realRandomAccess()

        val mousePosInGlobalSpace = sourceAccessInGlobalSpace.positionAsRealPoint().also {
            /* set position to display coords, at screen depth 0*/
            getMouseCoordinates(it)
            it.setPosition(0L, 2)
            /* transform display coords to global coords*/
            displayToGlobalCoordinates(it)
        }

        /* get the id of the source at the current mouse position. */
        return sourceAccessInGlobalSpace.setPositionAndGet(mousePosInGlobalSpace).integerLong
	}

	companion object {

		private val LOG = KotlinLogging.logger { }
	}
}
