package bdv.bigcat.viewer;

import java.util.function.Consumer;

import bdv.viewer.ViewerPanel;

public interface ViewerActor
{

	public Consumer< ViewerPanel > onAdd();

	public Consumer< ViewerPanel > onRemove();

}
