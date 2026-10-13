package org.janelia.saalfeldlab.paintera.stream

class ShowOnlySelectedInStreamToggle(private val stream: AbstractHighlightingARGBStream) {

	private var nonSelectionIsVisible = true

	private var alphaMemory = 0

	fun toggleNonSelectionVisibility() {
		if (nonSelectionIsVisible) {
			alphaMemory = stream.getAlpha()
			nonSelectionIsVisible = false
			stream.setAlpha(0)
		} else {
			stream.setAlpha(alphaMemory)
			nonSelectionIsVisible = true
		}
	}
}
