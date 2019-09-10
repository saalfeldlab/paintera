package org.janelia.saalfeldlab.paintera.stream

import bdv.viewer.Source
import gnu.trove.map.hash.TObjectIntHashMap
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream

import java.util.Optional
import java.util.function.Supplier

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
