package bdv.bigcat.viewer;

import java.util.function.Consumer;

import bdv.bigcat.viewer.bdvfx.ViewerPanelFX;

public interface ViewerActor
{

	public Consumer< ViewerPanelFX > onAdd();

	public Consumer< ViewerPanelFX > onRemove();

}
