package org.janelia.saalfeldlab.paintera.state.raw.n5

import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5Reader
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader

fun N5Reader.urlRepresentation() = when (this) {
    //TODO this needs to be updated
    is N5FSReader -> "n5://$basePath"
    is N5HDF5Reader -> "h5://${filename.absolutePath}"
    else -> "??://${toString()}"
}
