package org.janelia.saalfeldlab.paintera.meshes

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Subscription
import org.janelia.saalfeldlab.fx.extensions.plus
import org.janelia.saalfeldlab.paintera.meshes.managed.MeshManager

open class MeshInfo<T>(val key: T, open val manager: MeshManager<T>) {
	private var subscription : Subscription? = null

	val progressState: MeshProgressState?
		get() = manager.getStateFor(key)?.progress

	/**
	 * Subscribe to mesh state changes from the mesh manager via [MeshManager.subscribeToMeshState]
	 *
	 * @param valueSubscription to run when meshState changes
	 * @return the subscription
	 */
	fun subscribeToMeshState(other: Subscription? = null, valueSubscription: (MeshGenerator.State?) -> Unit) : Subscription {
		return manager.subscribeToMeshState(key, valueSubscription).also {
			subscription = it + subscription + other
		}
	}

	/**
	 * Unsubscribe from any current subscriptions
	 */
	fun unsubscribe() {
		subscription?.let { prevSub ->
			subscription = null
			prevSub.unsubscribe()
		}
	}


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
