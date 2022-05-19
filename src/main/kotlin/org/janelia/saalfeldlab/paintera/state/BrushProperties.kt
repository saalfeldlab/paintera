package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.fx.extensions.nonnull

class BrushProperties {

    private var boundTo: BrushProperties? = null

    internal val brushRadiusProperty = SimpleDoubleProperty(5.0)
    internal var brushRadius by brushRadiusProperty.nonnull()

    internal val brushDepthProperty = SimpleDoubleProperty(1.0)
    internal var brushDepth by brushDepthProperty.nonnull()

    internal fun copyFrom(other: BrushProperties) {
        brushRadius = other.brushRadius
        brushDepth = other.brushDepth
    }

    internal fun bindBidirectional(other: BrushProperties) {
        brushRadiusProperty.bindBidirectional(other.brushRadiusProperty)
        brushDepthProperty.bindBidirectional(other.brushDepthProperty)
        boundTo = other
    }

    internal fun unbindBidirectional() {
        boundTo?.let {
            brushRadiusProperty.unbindBidirectional(it.brushRadiusProperty)
            brushDepthProperty.unbindBidirectional(it.brushDepthProperty)
            boundTo = null
        }
    }
}
