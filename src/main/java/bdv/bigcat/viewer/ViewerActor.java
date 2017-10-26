package bdv.bigcat.viewer;

import java.util.function.Consumer;

import bdv.viewer.ViewerPanelFX;

public interface ViewerActor
{

	public Consumer< ViewerPanelFX > onAdd();

	public Consumer< ViewerPanelFX > onRemove();

}
