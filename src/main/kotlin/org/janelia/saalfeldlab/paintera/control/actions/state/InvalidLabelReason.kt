package org.janelia.saalfeldlab.paintera.control.actions.state

sealed class InvalidLabelReason(val reason: String, val solution: String) {

	data object NoLabelSelected : InvalidLabelReason("No Label Selected", "Select a label")

	data object LabelLocked : InvalidLabelReason("Label is Locked", "Unlock or select an unlocked label")
}
