package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.smooth

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategyModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.LabelSelectionModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphCommonModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphDirectionModel

interface SmoothLabelModel : MorphCommonModel, MorphDirectionModel, LabelSelectionModel, InfillStrategyModel {

	val gaussianThresholdProperty: DoubleProperty

	companion object {

		fun default(): SmoothLabelModel = object : SmoothLabelModel,
			MorphCommonModel by MorphCommonModel.default(),
			MorphDirectionModel by MorphDirectionModel.default(),
			LabelSelectionModel by LabelSelectionModel.default(),
			InfillStrategyModel by InfillStrategyModel.default() {

			override val gaussianThresholdProperty = SimpleDoubleProperty(0.5)
		}
	}
}
