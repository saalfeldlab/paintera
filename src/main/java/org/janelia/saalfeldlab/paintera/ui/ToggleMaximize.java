package org.janelia.saalfeldlab.paintera.ui;

import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;

public class ToggleMaximize
{

	private final GridConstraintsManager manager;

	private final int col;

	private final int row;

	public ToggleMaximize( final GridConstraintsManager manager, final int col, final int row )
	{
		super();
		this.manager = manager;
		this.col = col;
		this.row = row;
	}

	public void toggleFullScreen()
	{
		this.manager.maximize( row, col, 200 );
	}

}
