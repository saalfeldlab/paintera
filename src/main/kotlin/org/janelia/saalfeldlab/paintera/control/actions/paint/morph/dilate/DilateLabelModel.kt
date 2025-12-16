package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.dilate

import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategyModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.LabelSelectionModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphCommonModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphDirectionModel

interface DilateLabelModel : MorphCommonModel, LabelSelectionModel, InfillStrategyModel {
	companion object { 
		
		fun default(): DilateLabelModel = object : DilateLabelModel,
				MorphCommonModel by MorphCommonModel.default(),
				LabelSelectionModel by LabelSelectionModel.default(),
				InfillStrategyModel by InfillStrategyModel.default() { }
	}
}
