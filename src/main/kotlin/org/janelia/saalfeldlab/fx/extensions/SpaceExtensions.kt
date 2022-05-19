package org.janelia.saalfeldlab.fx.extensions

import bdv.fx.viewer.ViewerPanelFX
import net.imglib2.RealPoint
import net.imglib2.realtransform.AffineTransform3D


typealias DisplayPoint = RealPoint
typealias SourcePoint = RealPoint
typealias GlobalPoint = RealPoint

private val RealPoint.x : Double
    get() = getDoublePosition(0)

private val RealPoint.y : Double
    get() = getDoublePosition(0)

fun DisplayPoint.toSourcePoint(viewer: ViewerPanelFX, sourceTransform : AffineTransform3D) : SourcePoint {
    return SourcePoint(numDimensions()).also {
        viewer.displayToSourceCoordinates(x, y, sourceTransform, it)
    }
}

fun DisplayPoint.toGlobalPoint(viewer: ViewerPanelFX, sourceTransform : AffineTransform3D) : SourcePoint {
    return SourcePoint(numDimensions()).also {
        viewer.displayToSourceCoordinates(x, y, sourceTransform, it)
    }
}
