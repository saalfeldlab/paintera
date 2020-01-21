package org.janelia.saalfeldlab.paintera.meshes.managed.adaptive

import net.imglib2.img.cell.CellGrid
import net.imglib2.realtransform.AffineTransform3D
import org.janelia.saalfeldlab.paintera.viewer3d.ViewFrustum

class SceneUpdateParameters internal constructor(
    val viewFrustum: ViewFrustum,
    val eyeToWorldTransform: AffineTransform3D,
    val rendererGrids: Array<CellGrid>)
