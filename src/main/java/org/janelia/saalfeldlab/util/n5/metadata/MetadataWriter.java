package org.janelia.saalfeldlab.util.n5.metadata;

import org.janelia.saalfeldlab.n5.N5Writer;
import org.jetbrains.annotations.NotNull;

public interface MetadataWriter {

	void write(@NotNull N5Writer n5);

}
