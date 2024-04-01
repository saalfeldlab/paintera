package org.janelia.saalfeldlab.paintera.control.navigation;

import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class TransformConcatenator extends AffineTransformWithListeners {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final GlobalTransformManager manager;
	private final AffineTransformWithListeners displayTransform;
	private final AffineTransformWithListeners viewerSpaceToViewerTransform;

	public TransformConcatenator(
			final GlobalTransformManager manager,
			final AffineTransformWithListeners displayTransform,
			final AffineTransformWithListeners viewerSpaceToViewerTransform) {

		super();
		this.manager = manager;
		this.displayTransform = displayTransform;
		this.viewerSpaceToViewerTransform = viewerSpaceToViewerTransform;

		this.manager.addListener(tf -> update()); //global transform
		this.displayTransform.addListener(tf -> update()); //scale/translation
		this.viewerSpaceToViewerTransform.addListener(tf -> update()); //rotation
	}

	private void update() {

		synchronized (manager) {
			final AffineTransform3D globalTransform = new AffineTransform3D();
			manager.getTransform(globalTransform);
			LOG.trace("Concatenating: {} {} {}", displayTransform, viewerSpaceToViewerTransform, globalTransform);
			LOG.trace("Concatening with global-to-viewer={} this={}", viewerSpaceToViewerTransform, this);

			var globalToViewer = displayTransform.getTransformCopy()
					.concatenate(viewerSpaceToViewerTransform.getTransformCopy())
					.concatenate(globalTransform);

			LOG.trace("Concatenated transform: {}", globalToViewer);
			setTransform(globalToViewer);
		}
	}

}
