package org.janelia.saalfeldlab.paintera.control.navigation;

import bdv.viewer.TransformListener;
import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class TranslationController {
	private final GlobalTransformManager manager;
	private final AffineTransformWithListeners globalToViewerTransformListener;
	private final double[] delta = new double[3];
	private final AffineTransform3D globalTransform = new AffineTransform3D();
	private final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

	public TranslationController(
			final GlobalTransformManager manager,
			final AffineTransformWithListeners globalToViewerTransformListener) {

		this.manager = manager;
		this.globalToViewerTransformListener = globalToViewerTransformListener;
	}

	public void translate(final double dX, final double dY) {

		translate(dX, dY, 0.0);
	}

	public void translate(final double dX, final double dY, final double dZ) {

		translate(dX, dY, dZ, null);
	}

	public void translate(final double dX, final double dY, final double dZ, final Duration duration) {

		synchronized (manager) {

			manager.getTransform(globalTransform);
			globalToViewerTransformListener.getTransformCopy(globalToViewerTransform);
			/* undo global transform, left with only scale, rotation, translation in viewer space */
			globalToViewerTransform.concatenate(globalTransform.inverse());
			globalToViewerTransform.setTranslation(0.0, 0.0, 0.0);


			delta[0] = dX;
			delta[1] = dY;
			delta[2] = dZ;

			globalToViewerTransform.applyInverse(delta, delta);
			globalTransform.translate(delta);

			manager.setTransform(globalTransform, duration != null ? duration : Duration.ZERO);
		}

	}

	/*TODO: Move this to somewhere else*/
	public static final class TransformTracker implements TransformListener<AffineTransform3D> {

		private final AffineTransform3D transform;

		private final Object lock;

		public TransformTracker(final AffineTransform3D transform, final Object lock) {

			super();
			this.transform = transform;
			this.lock = lock;
		}

		@Override
		public void transformChanged(final AffineTransform3D t) {

			synchronized (lock) {
				transform.set(t);
			}
		}
	}

}
