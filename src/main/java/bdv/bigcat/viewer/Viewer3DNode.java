package bdv.bigcat.viewer;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Hub;
import graphics.scenery.Material;
import graphics.scenery.PointLight;
import graphics.scenery.Scene;
import graphics.scenery.SceneryElement;
import graphics.scenery.Settings;
import graphics.scenery.backends.Renderer;
import javafx.embed.swing.SwingNode;

public class Viewer3DNode extends SwingNode {
	boolean _isReady = false;
	JPanel panel;

	public Viewer3DNode() {
		initialize();
	}

	public void initialize() {
		Settings settings = new Settings();
		Hub hub = new Hub();
		Scene scene = new Scene();
		hub.add( SceneryElement.Settings, settings );
		Renderer renderer = Renderer.Factory.createRenderer(hub, "name", scene, 250, 250);
		hub.add(SceneryElement.Renderer, renderer);


		Material boxmaterial = new Material();
		boxmaterial.setAmbient(new GLVector(1.0f, 0.0f, 0.0f));
		boxmaterial.setDiffuse(new GLVector(0.0f, 1.0f, 0.0f));
		boxmaterial.setSpecular(new GLVector(1.0f, 1.0f, 1.0f));
		System.out.println(Viewer3DNode.class);
		boxmaterial.getTextures().put("diffuse", "data/helix.png");

		final Box box = new Box(new GLVector(1.0f, 1.0f, 1.0f));
		box.setMaterial(boxmaterial);
		box.setPosition(new GLVector(0.0f, 0.0f, 0.0f));

		scene.addChild(box);

		PointLight[] lights = new PointLight[2];

		for (int i = 0; i < lights.length; i++) {
			lights[i] = new PointLight();
			lights[i].setPosition(new GLVector(2.0f * i, 2.0f * i, 2.0f * i));
			lights[i].setEmissionColor(new GLVector(1.0f, 0.0f, 1.0f));
			lights[i].setIntensity(0.2f * (i + 1));
			lights[i].setIntensity(100.2f * (i + 1));
			lights[i].setLinear(0.0f);
			lights[i].setQuadratic(0.5f);
			scene.addChild(lights[i]);
		}

		Camera cam = new DetachedHeadCamera();
		cam.setPosition(new GLVector(0.0f, 0.0f, 5.0f));
		cam.perspectiveCamera(50.0f, renderer.getWindow().getWidth(), renderer.getWindow().getHeight(), 0.1f, 1000.0f);

		cam.setActive(true);
		scene.addChild(cam);

		Thread rotator = new Thread() {
			public void run() {
				while (true) {
					box.getRotation().rotateByAngleY(0.01f);
					box.setNeedsUpdate(true);

					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		rotator.start();

		SwingUtilities.invokeLater(() -> {
			panel = new JPanel();

			panel.setLayout(new BorderLayout());
			panel.add(renderer.getWindow().getClearglWindow().getNewtCanvasAWT(), BorderLayout.CENTER);
			this.setContent(panel);

			_isReady = true;
			setVisible(true);
		});
	}

	boolean isReady() {
		return _isReady;
	}

}
