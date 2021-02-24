package org.janelia.saalfeldlab.fx.ortho;

import bdv.fx.viewer.ViewerPanelFX;

import java.util.function.Consumer;

public class OnEnterOnExit {

  final Consumer<ViewerPanelFX> onEnter;

  final Consumer<ViewerPanelFX> onExit;

  public OnEnterOnExit(final Consumer<ViewerPanelFX> onEnter, final Consumer<ViewerPanelFX> onExit) {

	super();
	this.onEnter = onEnter;
	this.onExit = onExit;
  }

  public Consumer<ViewerPanelFX> onEnter() {

	return this.onEnter;
  }

  public Consumer<ViewerPanelFX> onExit() {

	return this.onExit;
  }

}
