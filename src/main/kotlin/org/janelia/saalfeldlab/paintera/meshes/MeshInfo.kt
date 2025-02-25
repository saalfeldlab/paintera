package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.BooleanProperty
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager

open class MeshInfo<T>(val key: T, open val manager: MeshManager<T>) {
	val progressState: MeshProgressState?
		get() = manager.getStateFor(key)?.progress


	override fun hashCode() = key.hashCode()

	override fun equals(obj: Any?): Boolean {
		val aClass: Class<out MeshInfo<*>?> = this.javaClass
		return aClass == obj!!.javaClass && key == aClass.cast(obj)!!.key
	}

	val meshSettings: MeshSettings
		get() = manager.getSettings(key)

	val isManagedProperty: BooleanProperty
		get() = manager.managedSettings.isManagedProperty(key)
}
