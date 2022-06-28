package org.janelia.saalfeldlab.paintera.ui.dialogs.opendialog

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty

class DatasetInfo {


    val ResolutionProperties: Array<DoubleProperty> = Array(3) { SimpleDoubleProperty() }

    val TranslationProperties: Array<DoubleProperty> = Array(3) { SimpleDoubleProperty() }

    val minProperty = SimpleDoubleProperty(Double.NaN)

    val maxProperty = SimpleDoubleProperty(Double.NaN)
}
