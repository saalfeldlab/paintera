package org.janelia.saalfeldlab.paintera.stream

class ShowOnlySelectedInStreamToggle(private val stream: AbstractHighlightingARGBStream) {

	private var nonSelectionIsVisible = true

	private var alphaMemory = 0

	fun toggleNonSelectionVisibility() {
		if (nonSelectionIsVisible) {
			alphaMemory = stream.alpha
			nonSelectionIsVisible = false
			stream.alpha = 0
		} else {
			stream.alpha = alphaMemory
			nonSelectionIsVisible = true
		}
	}


}
