package org.janelia.saalfeldlab.paintera.ui.dialogs


import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Version
import org.slf4j.LoggerFactory
import kotlin.jvm.java

internal object ReadMeDialog {


	private val LOG = LoggerFactory.getLogger(this::class.java)
	private val README_URL = getReadmeUrl()

	private fun getReadmeUrl(): String {
		val version = Version.VERSION_STRING
		val tag = if (version.endsWith("SNAPSHOT"))
			"master"
		else
			"^.*-SNAPSHOT-([A-Za-z0-9]+)$"
				.toRegex()
				.find(version)
				?.let { it.groupValues[1] }
				?: "paintera-$version"
		return "https://github.com/saalfeldlab/paintera/blob/$tag/README.md"
	}

	internal fun showReadme() {
		Paintera.application.hostServices?.showDocument(README_URL)
	}
}
