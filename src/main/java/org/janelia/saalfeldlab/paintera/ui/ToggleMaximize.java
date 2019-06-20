package org.janelia.saalfeldlab.paintera.ui;

import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedColumn;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager.MaximizedRow;
import org.janelia.saalfeldlab.fx.ortho.OrthogonalViews;

import javafx.scene.Node;

public class ToggleMaximize
{
	private final OrthogonalViews<? extends Node> orthogonalViews;
	private final GridConstraintsManager manager;

	private final MaximizedColumn col;
	private final MaximizedRow row;

	public ToggleMaximize(
			final OrthogonalViews<? extends Node> orthogonalViews,
			final GridConstraintsManager manager,
			final MaximizedColumn col,
			final MaximizedRow row)
	{
		this.orthogonalViews = orthogonalViews;
		this.manager = manager;
		this.col = col;
		this.row = row;
	}

	public void toggleMaximizeViewer()
	{
		if (manager.getMaximizedColumn() == MaximizedColumn.NONE && manager.getMaximizedRow() == MaximizedRow.BOTTOM)
			toggleMaximizeViewerAndOrthoslice();
		else
			manager.maximize(row, col, 0);
	}

	public void toggleMaximizeViewerAndOrthoslice()
	{
		if (manager.getMaximizedColumn() != MaximizedColumn.NONE && manager.getMaximizedRow() != MaximizedRow.NONE)
		{
			toggleMaximizeViewer();
			return;
		}

		if (col != MaximizedColumn.LEFT || row != MaximizedRow.BOTTOM)
		{
			final Node swappedNode = orthogonalViews.grid().getNodeAt(col.asIndex(), row.asIndex());
			final Node bottomLeftNode = orthogonalViews.grid().getNodeAt(MaximizedColumn.LEFT.asIndex(), MaximizedRow.BOTTOM.asIndex());

			orthogonalViews.pane().getChildren().remove(swappedNode);
			orthogonalViews.pane().getChildren().remove(bottomLeftNode);

			orthogonalViews.pane().add(swappedNode, MaximizedColumn.LEFT.asIndex(), MaximizedRow.BOTTOM.asIndex());
			orthogonalViews.pane().add(bottomLeftNode, col.asIndex(), row.asIndex());
		}
		manager.maximize(MaximizedRow.BOTTOM, 0);
	}
}
