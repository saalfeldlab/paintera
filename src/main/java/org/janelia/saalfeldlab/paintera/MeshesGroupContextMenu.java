package org.janelia.saalfeldlab.paintera;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.viewer3d.Group3DCoordinateTracker;

public class MeshesGroupContextMenu
{

	private final GlobalTransformManager manager;

	private final Group3DCoordinateTracker coordinateTracker;

	public MeshesGroupContextMenu(final GlobalTransformManager manager, final Group3DCoordinateTracker
			coordinateTracker)
	{
		super();
		this.manager = manager;
		this.coordinateTracker = coordinateTracker;
	}

	public ContextMenu createMenu()
	{
		final ContextMenu menu = new ContextMenu();

		if (coordinateTracker.isValidCoordinate())
		{
			final double[] coordinate   = coordinateTracker.getCoordinate();
			final MenuItem centerAtItem = new MenuItem("Center 2D ortho slices at " + new RealPoint(coordinate));
			centerAtItem.setOnAction(e -> {
				final AffineTransform3D globalTransform = new AffineTransform3D();
				manager.getTransform(globalTransform);
				globalTransform.apply(coordinate, coordinate);
				globalTransform.set(globalTransform.get(0, 3) - coordinate[0], 0, 3);
				globalTransform.set(globalTransform.get(1, 3) - coordinate[1], 1, 3);
				globalTransform.set(globalTransform.get(2, 3) - coordinate[2], 2, 3);
				manager.setTransform(globalTransform);
			});
			menu.getItems().add(centerAtItem);
		}

		return menu;
	}

}
