package org.janelia.saalfeldlab.fx.extensions

import javafx.scene.control.Spinner
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import net.imglib2.RealPoint
import org.janelia.saalfeldlab.paintera.control.ControlUtils

val MouseEvent.position: RealPoint
    get() = RealPoint(x, y)

fun Spinner<*>.addKeyAndScrollHandlers() {
    if (isEditable) {
        setOnKeyPressed {
            if (it.code == KeyCode.UP) {
                increment()
            } else if (it.code == KeyCode.DOWN) {
                decrement()
            }
        }
        setOnScroll {
            val scroll = ControlUtils.getBiggestScroll(it).toInt()
            if (scroll < 0) {
                decrement(-scroll)
            } else {
                increment(scroll)
            }
        }
    }
}
