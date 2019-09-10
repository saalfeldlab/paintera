package org.janelia.saalfeldlab.paintera

import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.Group
import javafx.scene.Node

class GroupWithVisibility(vararg child: Node) {

	private val children = listOf(*child)

	private val group = Group()

	private val _isVisible = SimpleBooleanProperty(false).also { it.addListener { _, _, newv -> update(newv) } }

	var isVisible
		get() = _isVisible.get()
		set(isVisible) = _isVisible.set(isVisible)

	val node: Node
		get() = group

	init {
	    update(_isVisible.get())
	}

	fun isVisibleProperty() = _isVisible

	private fun update(isVisible: Boolean) {
		if (isVisible) group.children.setAll(children) else group.children.clear()
	}



}
