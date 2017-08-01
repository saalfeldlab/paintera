package bdv.bigcat.viewer.atlas.mode;

import java.util.function.Consumer;

import bdv.viewer.ViewerPanel;

public interface Mode
{

	public Consumer< ViewerPanel > onEnter();

	public Consumer< ViewerPanel > onExit();

	public void enable();

	public void disable();

	public String getName();

}
