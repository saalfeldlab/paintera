package bdv.fx.viewer;

import javafx.scene.canvas.Canvas;

public class ResizableCanvas extends Canvas
{

	@Override
	public boolean isResizable()
	{
		return true;
	}

	@Override
	public double prefWidth( final double height )
	{
		return getWidth();
	}

	@Override
	public double prefHeight( final double width )
	{
		return getHeight();
	}

}
