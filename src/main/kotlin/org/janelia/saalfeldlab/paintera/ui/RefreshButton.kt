package org.janelia.saalfeldlab.paintera.ui

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon

object RefreshButton {

	@JvmOverloads
	@JvmStatic
	fun createFontAwesome(scale: Double = 1.0) = FontAwesome[FontAwesomeIcon.REFRESH, scale]

}
