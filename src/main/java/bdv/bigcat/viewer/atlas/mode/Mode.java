package bdv.bigcat.viewer.atlas.mode;

import java.util.function.Consumer;

import bdv.viewer.ViewerPanelFX;

public interface Mode
{

	public Consumer< ViewerPanelFX > onEnter();

	public Consumer< ViewerPanelFX > onExit();

	public void enable();

	public void disable();

	public String getName();

}
