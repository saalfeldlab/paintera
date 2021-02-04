package org.janelia.saalfeldlab.paintera.viewer3d;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import org.janelia.saalfeldlab.fx.event.MouseDragFX;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.control.ControlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point3D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import net.imglib2.Interval;
import net.imglib2.ui.TransformListener;

public class Scene3DHandler
{
	public static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final Viewer3DFX viewer;

	private static final double CENTER_X = 0;

	private static final double CENTER_Y = 0;

	private final Affine initialTransform = new Affine();

	private final Affine affine = new Affine();

	private final static double step = 1.0;// Math.PI / 180;

	private static final Point3D xNormal = new Point3D(1, 0, 0);

	private static final Point3D yNormal = new Point3D(0, 1, 0);

	private final List<TransformListener<Affine>> affineListeners = Collections.synchronizedList(new ArrayList<>());

	public Scene3DHandler(final Viewer3DFX viewer)
	{
		this.viewer = viewer;
		this.viewer.sceneGroup().getTransforms().add(affine);

		this.setAffine(initialTransform);
		addCommands();

		final Rotate rotate = new Rotate(
				"rotate 3d",
				new SimpleDoubleProperty(1.0),
				1.0,
				MouseEvent::isPrimaryButtonDown
		);
		rotate.installInto(viewer);

		final TranslateXY translateXY = new TranslateXY("translate", MouseEvent::isSecondaryButtonDown);
		translateXY.installIntoAsFilter(viewer);
	}

	public void setInitialTransformToInterval(final Interval interval)
	{
		initialTransform.setToIdentity();
		initialTransform.prependTranslation(
				-interval.min(0) - interval.dimension(0) / 2,
				-interval.min(1) - interval.dimension(1) / 2,
				-interval.min(2) - interval.dimension(2) / 2);
		final double sf = 1.0 / interval.dimension(0);
		initialTransform.prependScale(sf, sf, sf);
		InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(initialTransform));
	}

	private void addCommands()
	{
		viewer.addEventHandler(ScrollEvent.SCROLL, event -> {

			final double scroll = ControlUtils.getBiggestScroll(event);
			if ( scroll == 0)
			{
			  event.consume();
			  return;
			}

			double scrollFactor = scroll > 0 ? 1.05 : 1 / 1.05;

			if (event.isShiftDown())
			{
				if (event.isControlDown())
				{
					scrollFactor = scroll > 0 ? 1.01 : 1 / 1.01;
				}
				else
				{
					scrollFactor = scroll > 0 ? 2.05 : 1 / 2.05;
				}
			}

			final Affine target = affine.clone();
			target.prependScale(scrollFactor, scrollFactor, scrollFactor);
			InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(target));

			event.consume();
		});

		viewer.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode().equals(KeyCode.Z) && event.isShiftDown())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> this.setAffine(initialTransform));
				event.consume();
			}
		});

		viewer.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
			if (event.getCode().equals(KeyCode.P) && event.isControlDown())
			{
				InvokeOnJavaFXApplicationThread.invoke(() -> {
					saveAsPng();
				});
				event.consume();
			}
		});
	}

	private class Rotate extends MouseDragFX
	{

		private final SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final SimpleDoubleProperty factor = new SimpleDoubleProperty();

		private final static double SLOW_FACTOR = 0.1;

		private final static double NORMAL_FACTOR = 1;

		private final static double FAST_FACTOR = 2;

		private final Affine affineDragStart = new Affine();

		public Rotate(final String name, final DoubleProperty speed, final double factor, final Predicate<MouseEvent>
				eventFilter)
		{
			super(name, eventFilter, true, affine, false);
			LOG.trace("rotation");
			this.factor.set(factor);
			this.speed.set(speed.get() * this.factor.get());

			speed.addListener((obs, old, newv) -> this.speed.set(this.factor.get() * speed.get()));
			this.factor.addListener((obs, old, newv) -> this.speed.set(speed.get()));
		}

		@Override
		public void initDrag(final javafx.scene.input.MouseEvent event)
		{
			factor.set(NORMAL_FACTOR);

			if (event.isShiftDown())
			{
				if (event.isControlDown())
				{
					factor.set(SLOW_FACTOR);
				}
				else
				{
					factor.set(FAST_FACTOR);
				}
			}

			this.speed.set(speed.get() * this.factor.get());

			synchronized (getTransformLock())
			{
				affineDragStart.setToTransform(affine);
			}
		}

		@Override
		public void drag(final javafx.scene.input.MouseEvent event)
		{
			synchronized (getTransformLock())
			{
				LOG.trace("drag - rotate");
				final Affine target = new Affine(affineDragStart);
				final double dX     = event.getX() - getStartX();
				final double dY     = event.getY() - getStartY();
				final double v      = step * this.speed.get();
				LOG.trace("dx: {} dy: {}", dX, dY);

				target.prependRotation(v * dY, CENTER_X, CENTER_Y, 0, xNormal);
				target.prependRotation(v * -dX, CENTER_X, CENTER_Y, 0, yNormal);

				LOG.trace("target: {}", target);
				InvokeOnJavaFXApplicationThread.invoke(() -> setAffine(target));
			}
		}
	}

	private class TranslateXY extends MouseDragFX
	{
		public TranslateXY(final String name, final Predicate<MouseEvent> eventFilter)
		{
			super(name, eventFilter, true, affine, false);
			LOG.trace("translate");
		}

		@Override
		public void initDrag(final MouseEvent event)
		{
		}

		@Override
		public void drag(final MouseEvent event)
		{
			synchronized (getTransformLock())
			{
				LOG.trace("drag - translate");
				final double dX = event.getX() - getStartX();
				final double dY = event.getY() - getStartY();

				LOG.trace("dx " + dX + " dy: " + dY);

				final Affine target = affine.clone();
				target.prependTranslation(2 * dX / viewer.getHeight(), 2 * dY / viewer.getHeight());

				InvokeOnJavaFXApplicationThread.invoke(() -> setAffine(target));

				setStartX(getStartX() + dX);
				setStartY(getStartY() + dY);
			}
		}
	}

	private void saveAsPng()
	{
		final WritableImage image = viewer.scene().snapshot(new SnapshotParameters(), null);

		final FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Save 3d snapshot ");
		final SimpleObjectProperty<Optional<File>> fileProperty = new SimpleObjectProperty<>(Optional.empty());
		try
		{
			InvokeOnJavaFXApplicationThread.invokeAndWait(() -> {
				fileProperty.set(Optional.ofNullable(fileChooser.showSaveDialog(viewer.root().sceneProperty().get()
						.getWindow())));
			});
		} catch (final InterruptedException e)
		{
			e.printStackTrace();
		}
		if (fileProperty.get().isPresent())
		{
			File file = fileProperty.get().get();
			if (!file.getName().endsWith(".png"))
			{
				// TODO: now, it is overwritten if there is a file with the same
				// name and extension
				file = new File(file.getAbsolutePath() + ".png");
			}

			try
			{
				ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
			} catch (final IOException e)
			{
				// TODO: handle exception here
			}
		}
	}

	public void getAffine(final Affine target) {
		target.setToTransform(affine);
	}

	public void setAffine(final Affine affine) {
		this.affine.setToTransform(affine);
		this.affineListeners.forEach(l -> l.transformChanged(affine));
	}

	public void addAffineListener(final TransformListener<Affine> listener) {
		this.affineListeners.add(listener);
		listener.transformChanged(this.affine.clone());
	}

}
