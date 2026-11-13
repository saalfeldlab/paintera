package org.janelia.saalfeldlab.paintera.ui.dialogs

import javafx.scene.control.ButtonType
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import org.janelia.saalfeldlab.paintera.Paintera
import org.janelia.saalfeldlab.paintera.Version
import kotlin.jvm.optionals.getOrNull

internal object NewerProjectVersionDialog {

	private const val LATEST_RELEASE_URL = "https://github.com/saalfeldlab/paintera/releases/latest"


	private fun String.numericVersion(): List<Int>? =
		"^\\d+(\\.\\d+)*".toRegex().find(trim())?.value?.split('.')?.map { it.toInt() }

	internal fun isNewerThanCurrent(projectVersion: String?): Boolean {
		val project = projectVersion?.numericVersion() ?: return false
		val current = Version.VERSION_STRING?.numericVersion() ?: return false

		val paddedProject = project.asSequence() + generateSequence { 0 }
		val paddedCurrent = current.asSequence() + generateSequence { 0 }
		return paddedProject.zip(paddedCurrent)
			.take(maxOf(project.size, current.size))
			.firstOrNull { (projectPart, currentPart) -> projectPart != currentPart }
			?.let { (projectPart, currentPart) -> projectPart > currentPart }
			?: false
	}


	internal fun showAndWaitForContinue(): Boolean {
		val link = Hyperlink(LATEST_RELEASE_URL).apply {
			setOnAction { Paintera.application.hostServices?.showDocument(LATEST_RELEASE_URL) }
		}
		val alert = PainteraAlerts.confirmation("Continue", "Quit", false).apply {
			headerText = null
			dialogPane.content = VBox(
				Label("Loading a Paintera project with a version that is newer than current Paintera version."),
				Label("Consider updating to the latest Paintera version:"),
				link
			)
		}
		return alert.showAndWait().getOrNull() == ButtonType.OK
	}
}
