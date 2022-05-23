package org.janelia.saalfeldlab.paintera;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;

public class MeshesGroupContextMenu {

  private final GlobalTransformManager manager;

  public MeshesGroupContextMenu(final GlobalTransformManager manager) {

	this.manager = manager;
  }

  public ContextMenu createMenu(final double[] coordinate) {

	final ContextMenu menu = new ContextMenu();
	final MenuItem centerAtItem = new MenuItem(String.format(
			"Center 2D orthoslices at (%.3f, %.3f, %.3f)",
			coordinate[0],
			coordinate[1],
			coordinate[2]
	));
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
	menu.setAutoHide(true);
	return menu;
  }
}
