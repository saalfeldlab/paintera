package org.janelia.saalfeldlab.paintera.serialization

import bdv.fx.viewer.multibox.MultiBoxOverlayConfig
import bdv.fx.viewer.scalebar.ScaleBarOverlayConfig
import com.google.gson.annotations.Expose
import javafx.beans.property.SimpleBooleanProperty
import net.imglib2.realtransform.AffineTransform3D
import net.imglib2.ui.TransformListener
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager
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
	val gridConstraints = GridConstraintsManager()

	@Expose
	val menuBarConfig = MenuBarConfig()

	@Expose
	val sideBarConfig = SideBarConfig()

	@Expose
	val statusBarConfig = StatusBarConfig()

    @Expose
    val multiBoxOverlayConfig = MultiBoxOverlayConfig()

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
