package org.janelia.saalfeldlab.paintera.viewer3d;

import bdv.viewer.Source;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Affine;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.stream.Collectors;

public class BoundingBoxMeshes {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ObservableList<Source<?>> sources;

	private final Group sourceMeshes = new Group();

	public BoundingBoxMeshes(ObservableList<Source<?>> sources) {
		this.sources = sources;
		this.sources.addListener((ListChangeListener<Source<?>>) change -> updateMeshes());
		sourceMeshes.setVisible(true);
		updateMeshes();
	}

	private void updateMeshes() {
		sourceMeshes.getChildren().setAll(this
				.sources
				.stream()
				.map(source -> {
					final AffineTransform3D transform = new AffineTransform3D();
					source.getSourceTransform(0, 0, transform);
					final Interval interval = source.getSource(0, 0);
					final AffineTransform3D translation = new AffineTransform3D();
					translation.setTranslation(
							interval.dimension(0) / 2.0,
							interval.dimension(1) / 2.0,
							interval.dimension(2) / 2.0);

					// TODO does this need translation by 1/2 pixel?
					final Box box = new Box(interval.dimension(0), interval.dimension(1), interval.dimension(2));
					box.getTransforms().add(fromAffineTransform3D(transform.copy().concatenate(translation)));
					box.setMaterial(new PhongMaterial(Color.WHITE));
					box.setCullFace(CullFace.NONE);
					box.setDrawMode(DrawMode.LINE);
					box.setVisible(true);
					return box;
				})
				.collect(Collectors.toList()));
	}

	private static Affine fromAffineTransform3D(final AffineTransform3D affineTransform3D) {
		return new Affine(
				affineTransform3D.get(0, 0), affineTransform3D.get(0, 1), affineTransform3D.get(0, 2), affineTransform3D.get(0, 3),
				affineTransform3D.get(1, 0), affineTransform3D.get(1, 1), affineTransform3D.get(1, 2), affineTransform3D.get(1, 3),
				affineTransform3D.get(2, 0), affineTransform3D.get(2, 1), affineTransform3D.get(2, 2), affineTransform3D.get(2, 3));
	};

	public Node getSourceMeshes() {
		return this.sourceMeshes;
	}
}
