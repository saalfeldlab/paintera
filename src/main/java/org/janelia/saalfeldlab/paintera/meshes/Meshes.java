package org.janelia.saalfeldlab.paintera.meshes;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

public class Meshes {

	public static final Color DEFAULT_MESH_COLOR = new Color(1, 1, 1, 1.0);

	public static PhongMaterial painteraPhongMaterial() {
		return painteraPhongMaterial(DEFAULT_MESH_COLOR);
	}

	public static PhongMaterial painteraPhongMaterial(final Color color) {
		final PhongMaterial material = new PhongMaterial();
		material.setSpecularColor(color);
		material.setSpecularPower(50);
		return material;
	}

}
