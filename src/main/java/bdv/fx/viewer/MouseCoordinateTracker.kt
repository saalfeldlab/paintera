package bdv.fx.viewer

import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyDoubleWrapper
import javafx.scene.Node
import javafx.scene.input.MouseEvent.*
import org.janelia.saalfeldlab.fx.actions.ActionSet
import org.janelia.saalfeldlab.fx.extensions.nonnull

/**
 * Event filter that tracks both the x and y positions of the mouse inside a node as well whether or not the mouse is inside that node.
 */
class MouseCoordinateTracker {
    private val mouseXPropertyWrapper = ReadOnlyDoubleWrapper()

    /** read-only property that is tracks the x coordinate of the mouse relative to the node this was installed into. */
    val mouseXProperty: ReadOnlyDoubleProperty = mouseXPropertyWrapper.readOnlyProperty

    /**  x coordinate of the mouse relative to the node this was installed into. */
    val mouseX: Double by mouseXPropertyWrapper.nonnull()

    private val mouseYPropertyWrapper = ReadOnlyDoubleWrapper()

    /** read-only property that is tracks the y coordinate of the mouse relative to the node this was installed into. */
    val mouseYProperty: ReadOnlyDoubleProperty = mouseXPropertyWrapper.readOnlyProperty

    /** y coordinate of the mouse relative to the node this was installed into. */
    val mouseY: Double by mouseYPropertyWrapper.nonnull()


    private val isInsidePropertyWrapper = ReadOnlyBooleanWrapper(false)

    /** read-only property that is `true` if the mouse is inside the [Node] it was installed into, `false` otherwise */
    val isInsideProperty: ReadOnlyBooleanProperty = isInsidePropertyWrapper.readOnlyProperty

    /** `true` if the mouse is inside the [Node] it was installed into, `false` otherwise */
    var isInside: Boolean by isInsidePropertyWrapper.nonnull()


    val actions by lazy {
        ActionSet("Mouse Coordinate Tracker") {
            MOUSE_MOVED {
                ignoreKeys()
                consume = false
                filter = true
                onAction { event -> setPosition(event.x, event.y) }
            }
            MOUSE_DRAGGED {
                ignoreKeys()
                consume = false
                filter = true
                onAction { event -> setPosition(event.x, event.y) }
            }
            MOUSE_ENTERED {
                ignoreKeys()
                consume = false
                filter = true
                onAction { isInside = true }
            }
            MOUSE_EXITED {
                ignoreKeys()
                consume = false
                filter = true
                onAction { isInside = false }
            }
        }

    }

    @Synchronized
    fun getIsInside(): Boolean {
        return isInsideProperty.get()
    }


    @Synchronized
    private fun setPosition(x: Double, y: Double) {
        mouseXPropertyWrapper.set(x)
        mouseYPropertyWrapper.set(y)
    }
}
