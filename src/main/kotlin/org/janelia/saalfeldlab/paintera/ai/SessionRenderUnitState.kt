package org.janelia.saalfeldlab.paintera.ai

import org.janelia.saalfeldlab.bdv.fx.viewer.render.RenderUnitState

data class SessionRenderUnitState(val sessionId: String, val state: RenderUnitState) : RenderUnitState(state.transform, state.timepoint, state.sources, state.width, state.height) {

    override fun equals(other: Any?): Boolean {
        return state == other
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    companion object {
        fun RenderUnitState.withSessionId(sessionId: String) = SessionRenderUnitState(sessionId, this)
    }
}