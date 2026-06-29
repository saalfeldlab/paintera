package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

internal enum class UpdateSignal {
	Cancel,
	HidePreview,
	ShowPreview,
	Partial,
	Full,
	Finish;
}