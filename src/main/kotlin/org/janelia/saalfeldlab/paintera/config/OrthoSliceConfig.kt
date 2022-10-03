package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.BooleanProperty
import javafx.beans.property.DoubleProperty
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX

class OrthoSliceConfig(private val baseConfig: OrthoSliceConfigBase) {

    private val orthogonalViews = paintera.baseView.orthogonalViews()
    private val hasSources = paintera.baseView.sourceInfo().hasSources()
    private val topLeft = orthogonalViews.topLeft
    private val topRight = orthogonalViews.topRight
    private val bottomLeft = orthogonalViews.bottomLeft

    fun enableProperty(): BooleanProperty = this.baseConfig.isEnabledProperty

    fun showTopLeftProperty(): BooleanProperty = this.baseConfig.showTopLeftProperty()

    fun showTopRightProperty(): BooleanProperty = this.baseConfig.showTopRightProperty()

    fun showBottomLeftProperty(): BooleanProperty = this.baseConfig.showBottomLeftProperty()

    fun opacityProperty(): DoubleProperty = this.baseConfig.opacityProperty()

    fun shadingProperty(): DoubleProperty = this.baseConfig.shadingProperty()

    fun bindOrthoSlicesToConfig(orthoSlices: Map<OrthogonalViews.ViewerAndTransforms, OrthoSliceFX>) {
        val topLeftSlice = orthoSlices[topLeft]!!
        val topRightSlice = orthoSlices[topRight]!!
        val bottomLeftSlice = orthoSlices[bottomLeft]!!

        val enable = baseConfig.isEnabledProperty

        val isTopLeftVisible = topLeft.viewer().visibleProperty()
        val isTopRightVisible = topRight.viewer().visibleProperty()
        val isBottomLeftVisible = bottomLeft.viewer().visibleProperty()

        topLeftSlice.isVisibleProperty.bind(baseConfig.showTopLeftProperty().and(enable).and(hasSources).and(isTopLeftVisible))
        topRightSlice.isVisibleProperty.bind(baseConfig.showTopRightProperty().and(enable).and(hasSources).and(isTopRightVisible))
        bottomLeftSlice.isVisibleProperty.bind(baseConfig.showBottomLeftProperty().and(enable).and(hasSources).and(isBottomLeftVisible))

        listOf(topLeftSlice, topRightSlice, bottomLeftSlice).forEach { slice ->
            slice.opacityProperty().bind(baseConfig.opacityProperty())
            slice.shadingProperty().bind(baseConfig.shadingProperty())
        }
    }
}
