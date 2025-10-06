package org.janelia.saalfeldlab.paintera.control.actions.paint.morph.erode

import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.InfillStrategyModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.LabelSelectionModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphCommonModel
import org.janelia.saalfeldlab.paintera.control.actions.paint.morph.MorphDirectionModel

interface ErodeLabelModel : MorphCommonModel, LabelSelectionModel, InfillStrategyModel {
	companion object { 
		
		fun default(): ErodeLabelModel = object : ErodeLabelModel,
				MorphCommonModel by MorphCommonModel.default(),
				LabelSelectionModel by LabelSelectionModel.default(),
				InfillStrategyModel by InfillStrategyModel.default() { }
	}
}
