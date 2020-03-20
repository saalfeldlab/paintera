package org.janelia.saalfeldlab.paintera.ui

import javafx.scene.Group
import javafx.scene.shape.Polygon

object TriangleButton {

    fun create(size: Double) = create(size) { it.strokeWidth = 1.0 }

    fun create(size: Double, pathSetup: (Polygon) -> Unit) = Polygon(-size / 2, 0.0, 0.0, size / 2, size / 2, 0.0).also { pathSetup(it) }.let { Group(it) }

}
