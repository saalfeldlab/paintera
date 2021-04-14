package org.janelia.saalfeldlab.paintera.state.raw.n5

import org.janelia.saalfeldlab.paintera.state.SourceStateBackendN5
import org.janelia.saalfeldlab.paintera.state.raw.ConnectomicsRawBackend

interface AbstractN5BackendRaw<D, T> : ConnectomicsRawBackend<D, T>, SourceStateBackendN5<D, T>
