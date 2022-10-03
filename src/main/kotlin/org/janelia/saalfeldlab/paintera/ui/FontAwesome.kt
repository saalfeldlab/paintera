package org.janelia.saalfeldlab.paintera.ui

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView

class FontAwesome {

    companion object {
        @JvmOverloads
        @JvmStatic
        fun withIcon(icon: FontAwesomeIcon, scale: Double = 1.0) = FontAwesomeIconView(icon).apply {
            scaleX = scale
            scaleY = scale
            scaleZ = scale
        }

        operator fun get(icon: FontAwesomeIcon, scale: Double = 1.0) = withIcon(icon, scale)
    }

}
