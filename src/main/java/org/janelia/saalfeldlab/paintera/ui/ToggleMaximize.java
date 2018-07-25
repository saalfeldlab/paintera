package org.janelia.saalfeldlab.paintera.ui;

import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedColumn;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedRow;

public class ToggleMaximize
{

	private final GridConstraintsManager manager;

	private final MaximizedColumn col;

	private final MaximizedRow row;

	public ToggleMaximize(
			final GridConstraintsManager manager,
			final MaximizedColumn col,
			final MaximizedRow row)
	{
		super();
		this.manager = manager;
		this.col = col;
		this.row = row;
	}

	public void toggleFullScreen()
	{
		this.manager.maximize(row, col, 200);
	}

}
