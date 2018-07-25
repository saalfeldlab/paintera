package org.janelia.saalfeldlab.fx.ortho;

import java.util.function.Consumer;

import bdv.fx.viewer.ViewerPanelFX;

public class OnEnterOnExit
{

	final Consumer<ViewerPanelFX> onEnter;

	final Consumer<ViewerPanelFX> onExit;

	public OnEnterOnExit(final Consumer<ViewerPanelFX> onEnter, final Consumer<ViewerPanelFX> onExit)
	{
		super();
		this.onEnter = onEnter;
		this.onExit = onExit;
	}

	public Consumer<ViewerPanelFX> onEnter()
	{
		return this.onEnter;
	}

	public Consumer<ViewerPanelFX> onExit()
	{
		return this.onExit;
	}

}