package org.janelia.saalfeldlab.paintera.state.label

import org.janelia.saalfeldlab.paintera.data.DataSource
import org.janelia.saalfeldlab.paintera.id.IdService
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupNoBlocks

interface ReadOnlyConnectomicsLabelBackend<D,T> : ConnectomicsLabelBackend<D, T> {

    override fun createIdService(source: DataSource<D, T>) = IdService.IdServiceNotProvided()

    override fun createLabelBlockLookup(source: DataSource<D, T>) = LabelBlockLookupNoBlocks();
}
