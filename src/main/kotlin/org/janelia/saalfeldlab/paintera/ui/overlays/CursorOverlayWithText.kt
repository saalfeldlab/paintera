package org.janelia.saalfeldlab.paintera.ui.overlays

import org.janelia.saalfeldlab.bdv.fx.viewer.ViewerPanelFX
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

abstract class CursorOverlayWithText(viewerProperty: ObservableValue<ViewerPanelFX?>) : CursorOverlayInViewer(viewerProperty) {

	abstract val overlayText: String

	override fun drawOverlays(g: GraphicsContext) {
		if (visible && isMouseInside()) {
			position.apply {
				g.apply {
					applyCursorTextStyle()
					textAlign = TextAlignment.CENTER
					fillText(overlayText, x, y - 12)
					textAlign = TextAlignment.LEFT
				}
			}
			return
		}
	}

	override fun setCanvasSize(width: Int, height: Int) {}

	companion object {
		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
		private val TEXT_COLOR = Color.WHITE.deriveColor(0.0, 0.0, 0.0, .75)
		private const val TEXT_SIZE = 20.0


        fun GraphicsContext.applyCursorTextStyle() {
			fill = TEXT_COLOR
			font = Font.font(font.family, TEXT_SIZE)
		}
	}
}
