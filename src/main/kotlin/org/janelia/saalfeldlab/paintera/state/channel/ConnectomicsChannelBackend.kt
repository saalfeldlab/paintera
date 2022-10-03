package org.janelia.saalfeldlab.paintera.state.channel

import bdv.util.volatiles.SharedQueue
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource
import org.janelia.saalfeldlab.paintera.state.SourceStateBackend

interface ConnectomicsChannelBackend<D, T> : SourceStateBackend<D, T> {

    val channelSelection: IntArray
    val channelIndex: Int
    val numChannels: Int
        get() = channelSelection.size

    override fun createSource(
        queue: SharedQueue,
        priority: Int,
        name: String
    ): ChannelDataSource<D, T>

}
