package org.janelia.saalfeldlab.paintera.control.actions.paint.morph

open class OperationStatus(val text: String)

object Status {
	/** Nothing done yet */
	object Empty : OperationStatus("")
	/** a preview has been computed and is displayed; the operation can be applied */
	object Ready : OperationStatus("Ready")
	/** Applying the Operation */
	object Applying : OperationStatus("Applying...")
	/** The Operation is complete */
	object Done : OperationStatus("Done")
}




