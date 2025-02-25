package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import org.janelia.saalfeldlab.fx.extensions.nonnullVal

abstract class MeshProgressState {

	protected val writableTotalNumTasksProperty = SimpleIntegerProperty(0)
	val totalNumTasksProperty = ReadOnlyIntegerProperty.readOnlyIntegerProperty(writableTotalNumTasksProperty)
	val numTotalTasks by totalNumTasksProperty.nonnullVal()

	protected val writableCompletedNumTasksProperty = SimpleIntegerProperty(0)
	val completedNumTasksProperty = ReadOnlyIntegerProperty.readOnlyIntegerProperty(writableCompletedNumTasksProperty)
	val numCompletedTasks by completedNumTasksProperty.nonnullVal()
}