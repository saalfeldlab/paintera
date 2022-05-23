package org.janelia.saalfeldlab.paintera.control;

import bdv.fx.viewer.ViewerPanelFX;
import org.janelia.saalfeldlab.fx.ortho.OnEnterOnExit;

import java.util.function.Consumer;

public interface ToOnEnterOnExit {

  Consumer<ViewerPanelFX> getOnEnter();

  Consumer<ViewerPanelFX> getOnExit();

  default OnEnterOnExit onEnterOnExit() {

	return new OnEnterOnExit(getOnEnter(), getOnExit());
  }

}
