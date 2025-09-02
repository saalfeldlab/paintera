package org.janelia.saalfeldlab.paintera.ui

import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.scene.control.TitledPane
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.fx.extensions.createNonNullValueBinding
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews
import org.janelia.saalfeldlab.paintera.config.*
import org.janelia.saalfeldlab.paintera.paintera
import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX

class SettingsView private constructor(val vBox: VBox ) : TitledPane("Settings", vBox) {

	constructor() : this(VBox())

	private val navigationConfigNode = NavigationConfigNode(config = paintera.properties.navigationConfig, coordinateConfig = CoordinateConfigNode(paintera.baseView.manager()))

	private val multiBoxOverlayConfigNode = MultiBoxOverlayConfigNode(config = paintera.properties.multiBoxOverlayConfig)

	private val crosshairConfigNode = CrosshairConfigNode(paintera.properties.crosshairConfig)

	private val orthoSliceConfig = OrthoSliceConfig(paintera.properties.orthoSliceConfig)
	private val orthoSliceConfigNode = OrthoSliceConfigNode(orthoSliceConfig)

	private val viewer3DConfigNode = Viewer3DConfigNode(paintera.properties.viewer3DConfig)

	private val screenScaleConfigNode = ScreenScalesConfigNode(paintera.properties.screenScalesConfig)

	private val scaleBarConfigNode = ScaleBarOverlayConfigNode(paintera.properties.scaleBarOverlayConfig)

	private val bookmarkConfigNode = BookmarkConfigNode(paintera.properties.bookmarkConfig) {
		paintera.baseView.manager().setTransformAndAnimate(it.globalTransformCopy, paintera.properties.bookmarkConfig.getTransitionTime())
		paintera.baseView.viewer3D().setAffine(it.viewer3DTransformCopy, paintera.properties.bookmarkConfig.getTransitionTime())
	}

	private val arbitraryMeshConfigNode = ArbitraryMeshConfigNode(paintera.gateway.triangleMeshFormat, paintera.properties.arbitraryMeshConfig)

	private val loggingConfigNode = LoggingConfigNode(paintera.properties.loggingConfig)

	private val segmentAnythingConfigNode = SegmentAnythingConfigNode(paintera.properties.segmentAnythingConfig)
	private val painteraDirectoriesConfigNode = PainteraDirectoriesConfigNode(paintera.properties.painteraDirectoriesConfig)

	init {
		vBox.apply {
			children += navigationConfigNode.getContents()
			children += multiBoxOverlayConfigNode.contents
			children += crosshairConfigNode.getContents()
			children += orthoSliceConfigNode.getContents()
			children += viewer3DConfigNode.contents
			children += scaleBarConfigNode
			children += bookmarkConfigNode
			children += arbitraryMeshConfigNode
			children += segmentAnythingConfigNode
			children += painteraDirectoriesConfigNode
			children += screenScaleConfigNode.contents
			children += loggingConfigNode.node
		}
		vBox.padding = Insets(0.0, 0.0, 0.0, 10.4)
		val nestedWidthBinding = maxWidthProperty().createNonNullValueBinding { it.toDouble() - 10.4 }
		vBox.maxWidthProperty().bind(nestedWidthBinding)
		vBox.minWidthProperty().set(0.0)
		isExpanded = false
	}

	fun bookmarkConfigNode() = bookmarkConfigNode

	fun getMeshGroup() = arbitraryMeshConfigNode.getMeshGroup()

	fun bindOrthoSlices(orthoSlices: Map<OrthogonalViews.ViewerAndTransforms, OrthoSliceFX>) {
		orthoSliceConfig.bindOrthoSlicesToConfig(orthoSlices)
	}

	fun bindWidth(targetWidth: SimpleDoubleProperty) {
		maxWidthProperty().bind(targetWidth)
		minWidthProperty().bind(targetWidth)
		prefWidthProperty().bind(targetWidth)
	}


}
