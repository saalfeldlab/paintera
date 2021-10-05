package org.janelia.saalfeldlab.paintera.state.label.n5

import org.janelia.saalfeldlab.paintera.state.label.ReadOnlyConnectomicsLabelBackend

interface ReadOnlyN5Backend<D, T> : N5Backend<D, T>, ReadOnlyConnectomicsLabelBackend<D, T>
