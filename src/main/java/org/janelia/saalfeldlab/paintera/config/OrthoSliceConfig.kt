package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import javafx.beans.value.ObservableBooleanValue
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.PainteraBaseView
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX

class OrthoSliceConfig(
        private val baseConfig: OrthoSliceConfigBase,
        private val isTopLeftVisible: ObservableBooleanValue,
        private val isTopRightVisible: ObservableBooleanValue,
        private val isBottomLeftVisible: ObservableBooleanValue,
        private val hasSources: ObservableBooleanValue) {

//	properties.orthoSliceConfig,
//	baseView.orthogonalViews().topLeft().viewer().visibleProperty(),
//	baseView.orthogonalViews().topRight().viewer().visibleProperty(),
//	baseView.orthogonalViews().bottomLeft().viewer().visibleProperty(),
//	baseView.sourceInfo().hasSources()

		constructor (
			baseConfig: OrthoSliceConfigBase,
			viewer: PainteraBaseView,
			viewerToSlice: (OrthogonalViews.ViewerAndTransforms) -> OrthoSliceFX): this(
				baseConfig,
				viewer.orthogonalViews().topLeft().viewer().visibleProperty(),
				viewer.orthogonalViews().topRight().viewer().visibleProperty(),
				viewer.orthogonalViews().bottomLeft().viewer().visibleProperty(),
				viewer.sourceInfo().hasSources()) {
			bindOrthoSlicesToConfig(
				viewerToSlice(viewer.orthogonalViews().topLeft()),
				viewerToSlice(viewer.orthogonalViews().topRight()),
				viewerToSlice(viewer.orthogonalViews().bottomLeft()))
		}

    fun enableProperty(): BooleanProperty = this.baseConfig.isEnabledProperty

    fun showTopLeftProperty(): BooleanProperty = this.baseConfig.showTopLeftProperty()

    fun showTopRightProperty(): BooleanProperty = this.baseConfig.showTopRightProperty()

    fun showBottomLeftProperty(): BooleanProperty = this.baseConfig.showBottomLeftProperty()

    fun opacityProperty(): DoubleProperty = this.baseConfig.opacityProperty()

    fun shadingProperty(): DoubleProperty = this.baseConfig.shadingProperty()

    fun bindOrthoSlicesToConfig(
            topLeft: OrthoSliceFX,
            topRight: OrthoSliceFX,
            bottomLeft: OrthoSliceFX) {
        val enable = baseConfig.isEnabledProperty
        topLeft.isVisibleProperty.bind(baseConfig.showTopLeftProperty().and(enable).and(hasSources).and(isTopLeftVisible))
        topRight.isVisibleProperty.bind(baseConfig.showTopRightProperty().and(enable).and(hasSources).and(isTopRightVisible))
        bottomLeft.isVisibleProperty.bind(baseConfig.showBottomLeftProperty().and(enable).and(hasSources).and(isBottomLeftVisible))

        topLeft.opacityProperty().bind(baseConfig.opacityProperty())
        topRight.opacityProperty().bind(baseConfig.opacityProperty())
        bottomLeft.opacityProperty().bind(baseConfig.opacityProperty())

        topLeft.shadingProperty().bind(baseConfig.shadingProperty())
        topRight.shadingProperty().bind(baseConfig.shadingProperty())
        bottomLeft.shadingProperty().bind(baseConfig.shadingProperty())
    }
}
