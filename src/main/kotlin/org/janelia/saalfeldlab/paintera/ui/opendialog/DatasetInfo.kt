package org.janelia.saalfeldlab.paintera.ui.opendialog

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty

class DatasetInfo {


    val spatialResolutionProperties: Array<DoubleProperty> = Array(3) { SimpleDoubleProperty() }

    val spatialOffsetProperties: Array<DoubleProperty> = Array(3) { SimpleDoubleProperty() }

    val minProperty = SimpleDoubleProperty(Double.NaN)

    val maxProperty = SimpleDoubleProperty(Double.NaN)
}
