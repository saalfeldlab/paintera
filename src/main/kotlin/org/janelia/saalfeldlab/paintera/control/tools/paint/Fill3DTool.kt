package org.janelia.saalfeldlab.paintera.control.tools.paint

import bdv.fx.viewer.ViewerPanelFX
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.Cursor
import javafx.scene.input.KeyEvent.KEY_PRESSED
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.fx.UtilityTask
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.actions.painteraActionSet
import org.janelia.saalfeldlab.fx.extensions.LazyForeignValue
import org.janelia.saalfeldlab.fx.extensions.createNullableValueBinding
import org.janelia.saalfeldlab.fx.extensions.nonnull
import org.janelia.saalfeldlab.fx.extensions.nullable
import org.janelia.saalfeldlab.fx.ui.ScaleView
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread
import org.janelia.saalfeldlab.paintera.LabelSourceStateKeys
import org.janelia.saalfeldlab.paintera.control.ControlUtils
import org.janelia.saalfeldlab.paintera.control.actions.PaintActionType
import org.janelia.saalfeldlab.fx.ui.GlyphScaleView
import org.janelia.saalfeldlab.paintera.control.modes.ToolMode
import org.janelia.saalfeldlab.paintera.control.paint.FloodFill
import org.janelia.saalfeldlab.paintera.meshes.MeshSettings
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.overlays.CursorOverlayWithText

class Fill3DTool(activeSourceStateProperty: SimpleObjectProperty<SourceState<*, *>?>, mode: ToolMode? = null) : PaintTool(activeSourceStateProperty, mode) {

	override val graphic = { ScaleView().also { it.styleClass += "fill-3d" } }

	override val name = "Fill 3D"
	override val keyTrigger = LabelSourceStateKeys.FILL_3D


	private val floodFillTaskProperty = SimpleObjectProperty<UtilityTask<*>?>()
	private var floodFillTask: UtilityTask<*>? by floodFillTaskProperty.nullable()

	val fill by LazyForeignValue({ statePaintContext }) {
		with(it!!) {
			FloodFill(
				activeViewerProperty.createNullableValueBinding { vat -> vat?.viewer() },
				dataSource,
				assignment,
				{ interval ->
					val sourceToGlobal = AffineTransform3D().also { transform ->
						dataSource.getSourceTransform(dataSource.currentMask.info, transform)
					}
					paintera.baseView.orthogonalViews().requestRepaint(sourceToGlobal.estimateBounds(interval))
				},
				{ MeshSettings.Defaults.Values.isVisible }
			)
		}
	}

	private val overlay by lazy {
		Fill3DOverlay(activeViewerProperty.createNullableValueBinding { it?.viewer() })
	}

	private val fillIsRunningProperty = SimpleBooleanProperty(false, "Flood Fill 3D is running")
	private var fillIsRunning by fillIsRunningProperty.nonnull()

	override fun activate() {
		super.activate()
		overlay.visible = true
	}

	override fun deactivate() {
		if (fillIsRunning) return

		overlay.visible = false
		super.deactivate()
	}

	override val actionSets: MutableList<ActionSet> by LazyForeignValue({ activeViewerAndTransforms }) {
		mutableListOf(
			*super.actionSets.toTypedArray(),
			painteraActionSet("change brush depth", PaintActionType.SetBrushDepth) {
				ScrollEvent.SCROLL {
					keysExclusive = false
					onAction { changeBrushDepth(-ControlUtils.getBiggestScroll(it)) }
				}
			},
			painteraActionSet("fill", PaintActionType.Fill) {
				MouseEvent.MOUSE_PRESSED(MouseButton.PRIMARY) {
					keysExclusive = false
					verifyEventNotNull()
					onAction {
						lateinit var setFalseAndRemoveListener: ChangeListener<Boolean>
						setFalseAndRemoveListener = ChangeListener { obs, _, isBusy ->
							if (isBusy) {
								overlay.cursor = Cursor.WAIT
							} else {
								overlay.cursor = Cursor.CROSSHAIR
								if (!paintera.keyTracker.areKeysDown(keyTrigger) && !enteredWithoutKeyTrigger) {
									InvokeOnJavaFXApplicationThread { mode?.switchTool(mode.defaultTool) }
								}
								obs.removeListener(setFalseAndRemoveListener)
							}
						}

						fillIsRunningProperty.set(true)
						fill.fillAt(it!!.x, it.y, statePaintContext?.paintSelection).also { task ->
							floodFillTask = task

							paintera.baseView.isDisabledProperty.addListener(setFalseAndRemoveListener)
							paintera.baseView.disabledPropertyBindings[this] = fillIsRunningProperty

							if (task.isDone) {
								/* If its already done, do this now*/
								fillIsRunningProperty.set(false)
								paintera.baseView.disabledPropertyBindings -= this
								statePaintContext?.refreshMeshes?.invoke()
								floodFillTask = null
							} else {
								/* Otherwise, do it when it's done */
								task.onEnd(append = true) {
									floodFillTask = null
									fillIsRunningProperty.set(false)
									paintera.baseView.disabledPropertyBindings -= this
									statePaintContext?.refreshMeshes?.invoke()
								}
							}
						}
					}
				}
			},
			painteraActionSet(LabelSourceStateKeys.CANCEL, ignoreDisable = true) {
				KEY_PRESSED(LabelSourceStateKeys.CANCEL) {
					name = "cancel Fill 3D"
					graphic = { GlyphScaleView(FontAwesomeIconView().apply{ styleClass += "reject" }).apply { styleClass += "ignore-disable"} }
					filter = true
					verify { floodFillTask != null }
					onAction {
						floodFillTask?.cancel()
						fillIsRunningProperty.set(false)
						mode?.switchTool(mode.defaultTool)
					}
				}
			}
		)
	}

	private class Fill3DOverlay(viewerProperty: ObservableValue<ViewerPanelFX?>, override val overlayText: String = "Fill 3D") :
		CursorOverlayWithText(viewerProperty)
}

