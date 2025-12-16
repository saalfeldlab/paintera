package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

open class OperationStatus(val text: String)

object Status {
	object Empty : OperationStatus("")
	object Done : OperationStatus("Done")
	object Applying : OperationStatus("Applying...")
}




