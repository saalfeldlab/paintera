package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.binding.ObjectExpression
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.util.Subscription
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager

open class MeshInfo<T>(val key: T, open val manager: MeshManager<T>) {

	private var subscription: Subscription? = null

	val meshStateProperty: ObjectExpression<MeshGenerator.State?> by lazy {
		val property = ReadOnlyObjectWrapper<MeshGenerator.State?>()
		subscription = manager.subscribeToMeshState(key) { property.set(it) }
		property.readOnlyProperty
	}

	fun dispose() {
		subscription?.unsubscribe()
		subscription = null
	}

	override fun hashCode() = key.hashCode()

	override fun equals(obj: Any?): Boolean {
		val aClass: Class<out MeshInfo<*>?> = this.javaClass
		return obj != null && aClass == obj.javaClass && key == aClass.cast(obj)?.key
	}

	val meshSettings: MeshSettings
		get() = manager.getSettings(key)

	val isManagedProperty: BooleanProperty
		get() = manager.managedSettings.managedIndividuallyProperty(key)
}
