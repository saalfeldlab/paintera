package org.janelia.saalfeldlab.paintera

import org.janelia.saalfeldlab.control.mcu.MCUControlPanel
import org.janelia.saalfeldlab.control.mcu.XTouchMiniMCUControlPanel

object DeviceManager {

    val xTouchMini: MCUControlPanel? by lazy {
        try {
            XTouchMiniMCUControlPanel.build()
        } catch (e: Exception) {
            null
        }
    }
}