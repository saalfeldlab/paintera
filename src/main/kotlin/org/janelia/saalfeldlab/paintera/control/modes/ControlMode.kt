package org.janelia.saalfeldlab.paintera.control.modes

import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import org.janelia.saalfeldlab.paintera.control.actions.AllowedActions


object AppControlMode : ControlMode {

    override val allowedActions = AllowedActions.APP_CONTROL
    override val statusProperty: StringProperty = SimpleStringProperty()

}


