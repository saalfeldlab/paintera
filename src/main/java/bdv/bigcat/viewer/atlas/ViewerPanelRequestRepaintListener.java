package bdv.bigcat.viewer.atlas;

import bdv.bigcat.viewer.state.SelectedIds;
import bdv.bigcat.viewer.state.StateListener;
import bdv.viewer.ViewerPanel;

public class ViewerPanelRequestRepaintListener implements StateListener< SelectedIds >
{

	private final ViewerPanel viewer;

	public ViewerPanelRequestRepaintListener( final ViewerPanel viewer )
	{
		super();
		this.viewer = viewer;
	}

	@Override
	public void stateChanged()
	{
		System.out.println( viewer );
		viewer.requestRepaint();
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( o instanceof ViewerPanelRequestRepaintListener )
			return this.viewer != null && this.viewer == ( ( ViewerPanelRequestRepaintListener ) o ).viewer;
		return false;
	}

}
