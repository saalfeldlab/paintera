package org.janelia.saalfeldlab.paintera.state.channel

import org.janelia.saalfeldlab.paintera.data.ChannelDataSource
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend

interface ConnectomicsChannelBackend<D, T> : SourceStateBackend<D, T> {
	override val source: ChannelDataSource<D, T>
}
