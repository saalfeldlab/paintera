package org.janelia.saalfeldlab.paintera.state.channel.n5

import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.channel.ConnectomicsChannelBackend

interface AbstractN5BackendChannel<D, T> : ConnectomicsChannelBackend<D, T>, SourceStateBackendN5<D, T>
