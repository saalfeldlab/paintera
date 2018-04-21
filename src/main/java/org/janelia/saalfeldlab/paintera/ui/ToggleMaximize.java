package org.janelia.saalfeldlab.paintera.ui;

import java.util.Collection;

import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;

import javafx.scene.Node;

public class ToggleMaximize
{

	private final Node toMaximize;

	private final Collection< Node > others;

	private final GridConstraintsManager manager;

	private final int col;

	private final int row;

	private boolean isFullScreen;

	public ToggleMaximize( final Node toMaximize, final Collection< Node > others, final GridConstraintsManager manager, final int col, final int row )
	{
		super();
		this.toMaximize = toMaximize;
		this.others = others;
		this.manager = manager;
		this.col = col;
		this.row = row;
	}

	public void toggleFullScreen()
	{
		if ( isFullScreen )
		{
			this.manager.resetToLast();
			others.forEach( n -> n.setVisible( true ) );
			isFullScreen = false;
		}
		else
		{
			others.forEach( n -> n.setVisible( false ) );
			toMaximize.setVisible( true );
			this.manager.maximize( row, col, 200 );
			isFullScreen = true;
		}
	}

}
