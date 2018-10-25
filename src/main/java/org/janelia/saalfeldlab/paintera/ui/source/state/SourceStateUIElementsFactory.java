package org.janelia.saalfeldlab.paintera.ui.source.state;

import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;
import org.janelia.saalfeldlab.util.SciJavaUtils;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

public interface SourceStateUIElementsFactory<T extends SourceState> extends SciJavaPlugin, SciJavaUtils.HasTargetClass<T> {

	BindUnbindAndNodeSupplier create(T state);

}
