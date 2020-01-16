package org.janelia.saalfeldlab.paintera.config

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import org.janelia.saalfeldlab.paintera.control.Navigation2
import org.janelia.saalfeldlab.paintera.control.navigation.ButtonRotationSpeedConfig

class NavigationConfig {

    private val allowRotations = SimpleBooleanProperty(true)

    private val buttonRotationSpeeds = ButtonRotationSpeedConfig()

    fun allowRotationsProperty(): BooleanProperty {
        return this.allowRotations
    }

	fun bindNavigationToConfig(navigation: Navigation2) {
		navigation.allowRotationsProperty().bind(this.allowRotations)
		navigation.bindTo(this.buttonRotationSpeeds)
	}

    fun buttonRotationSpeeds(): ButtonRotationSpeedConfig {
        return this.buttonRotationSpeeds
    }

    fun set(that: NavigationConfig) {
        this.allowRotations.set(that.allowRotations.get())
        this.buttonRotationSpeeds.slow.set(that.buttonRotationSpeeds.slow.get())
        this.buttonRotationSpeeds.fast.set(that.buttonRotationSpeeds.fast.get())
        this.buttonRotationSpeeds.regular.set(that.buttonRotationSpeeds.regular.get())
    }

}
