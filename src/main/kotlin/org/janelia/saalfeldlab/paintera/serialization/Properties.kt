package org.janelia.saalfeldlab.paintera.serialization

import org.janelia.saalfeldlab.bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import org.janelia.saalfeldlab.bdv.fx.viewer.scalebar.ScaleBarOverlayConfig
import bdv.viewer.TransformListener
import com.google.gson.annotations.Expose
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.config.*
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig

class Properties : TransformListener<AffineTransform3D> {

	@Expose
	val globalTransform = AffineTransform3D()

	@Expose
	val windowProperties = WindowProperties()

	@Expose
	val crosshairConfig = CrosshairConfig()

	@Expose
	val orthoSliceConfig = OrthoSliceConfigBase()

	@Expose
	val navigationConfig = NavigationConfig()

	@Expose
	val viewer3DConfig = Viewer3DConfig()

	@Expose
	val screenScalesConfig = ScreenScalesConfig()

	@Expose
	val scaleBarOverlayConfig = ScaleBarOverlayConfig()

	@Expose
	val bookmarkConfig = BookmarkConfig()

	@Expose
	val arbitraryMeshConfig = ArbitraryMeshConfig()

	@Expose
	val menuBarConfig = MenuBarConfig()

	@Expose
	val sideBarConfig = SideBarConfig()

	@Expose
	val toolBarConfig = ToolBarConfig()

	@Expose
	val statusBarConfig = StatusBarConfig()

	@Expose
	val loggingConfig = LoggingConfig()

	@Expose
	val multiBoxOverlayConfig = MultiBoxOverlayConfig()

	@Expose
	val segmentAnythingConfig = SegmentAnythingConfig()

	@Expose
	val painteraDirectoriesConfig = PainteraDirectoriesConfig()

	@Transient
	val keyAndMouseConfig = KeyAndMouseConfig()

	@Transient
	private val transformDirty = SimpleBooleanProperty(false)

	override fun transformChanged(transform: AffineTransform3D) {
		globalTransform.set(transform)
		transformDirty.set(true)
	}

	fun globalTransformCopy(): AffineTransform3D {
		return this.globalTransform.copy()
	}

	fun setGlobalTransformClean() {
		this.transformDirty.set(true)
	}
}
