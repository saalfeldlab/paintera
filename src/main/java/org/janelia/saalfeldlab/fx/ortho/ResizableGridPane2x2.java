package org.janelia.saalfeldlab.fx.ortho;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;

/**
 * A wrapper around {@link GridPane} that holds for children organized in a 2x2 grid. The underlying
 * {@link GridPane} is exposed through {@link #pane()} and can be managed with a {@link GridConstraintsManager}
 * that is passed to {@link #manage(GridConstraintsManager)}.
 *
 * @param <TL> type of top left child
 * @param <TR> type of top right child
 * @param <BL> type of bottom left child
 * @param <BR> type of bottom right child
 */
public class ResizableGridPane2x2<TL extends Node, TR extends Node, BL extends Node, BR extends Node>
{

	private final GridPane grid = new GridPane();

	private final ObjectProperty<TL> topLeft = new SimpleObjectProperty<>();

	private final ObjectProperty<TR> topRight = new SimpleObjectProperty<>();

	private final ObjectProperty<BL> bottomLeft = new SimpleObjectProperty<>();

	private final ObjectProperty<BR> bottomRight = new SimpleObjectProperty<>();

	{
		topLeft.addListener((obs, oldv, newv) -> replace(grid, oldv, newv, 0, 0));
		topRight.addListener((obs, oldv, newv) -> replace(grid, oldv, newv, 1, 0));
		bottomLeft.addListener((obs, oldv, newv) -> replace(grid, oldv, newv, 0, 1));
		bottomRight.addListener((obs, oldv, newv) -> replace(grid, oldv, newv, 1, 1));
	}

	/**
	 *
	 * @param topLeft top left child
	 * @param topRight top right child
	 * @param bottomLeft bottom left child
	 * @param bottomRight bottom right child
	 */
	public ResizableGridPane2x2(
			final TL topLeft,
			final TR topRight,
			final BL bottomLeft,
			final BR bottomRight)
	{
		super();
		grid.setHgap(1);
		grid.setVgap(1);
		this.topLeft.set(topLeft);
		this.topRight.set(topRight);
		this.bottomLeft.set(bottomLeft);
		this.bottomRight.set(bottomRight);
	}

	/**
	 *
	 * @return underlying {@link GridPane}
	 */
	public GridPane pane()
	{
		return this.grid;
	}

	/**
	 *
	 * @return proprty tracking child at top left
	 */
	public ObjectProperty<TL> topLeftProperty()
	{
		return topLeft;
	}

	/**
	 *
	 * @return proprty tracking child at top right
	 */
	public ObjectProperty<TR> topRightProperty()
	{
		return topRight;
	}

	/**
	 *
	 * @return proprty tracking child at bottom left
	 */
	public ObjectProperty<BL> bottomLeftProperty()
	{
		return bottomLeft;
	}

	/**
	 *
	 * @return proprty tracking child at bottom right
	 */
	public ObjectProperty<BR> bottomRightProperty()
	{
		return bottomRight;
	}

	/**
	 *
	 * @return child at top left
	 */
	public TL getTopLeft()
	{
		return topLeftProperty().get();
	}

	/**
	 *
	 * @return child at top right
	 */
	public TR getTopRight()
	{
		return topRightProperty().get();
	}

	/**
	 *
	 * @return child at bottom left
	 */
	public BL getBototmLeft()
	{
		return bottomLeftProperty().get();
	}

	/**
	 *
	 * @return child at bottom right
	 */
	public BR getBottomRight()
	{
		return bottomRightProperty().get();
	}

	/**
	 * Manage the underlying {@link GridPane} with a {@link GridConstraintsManager}.
	 *
	 * @param manager controls grid cell proportions
	 */
	public void manage(final GridConstraintsManager manager)
	{
		manager.manageGrid(this.grid);
	}

	public Node getNodeAt(final int col, final int row)
	{
		for (final Node child : grid.getChildren())
			if (GridPane.getColumnIndex(child) == col && GridPane.getRowIndex(child) == row)
				return child;
		return null;
	}

	private static void replace(final GridPane grid, final Node oldValue, final Node newValue, final int col, final int
			row)
	{
		grid.getChildren().remove(oldValue);
		grid.add(newValue, col, row);
	}
}
