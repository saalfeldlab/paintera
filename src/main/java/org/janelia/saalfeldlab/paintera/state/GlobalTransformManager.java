package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.TransformListener;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.util.SimilarityTransformInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GlobalTransformManager {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ArrayList<TransformListener<AffineTransform3D>> listeners;

	private final AffineTransform3D affine;

	@SafeVarargs
	public GlobalTransformManager(final TransformListener<AffineTransform3D>... listeners) {

		this(new AffineTransform3D(), listeners);
	}

	@SafeVarargs
	public GlobalTransformManager(final AffineTransform3D affine, final TransformListener<AffineTransform3D>...
			listeners) {

		this(affine, Arrays.asList(listeners));
	}

	public GlobalTransformManager(final AffineTransform3D affine, final List<TransformListener<AffineTransform3D>>
			listeners) {

		super();
		this.listeners = new ArrayList<>();
		this.affine = affine;
		listeners.forEach(this::addListener);
	}

	public synchronized void setTransformAndAnimate(final AffineTransform3D affine, @Nullable final Duration duration) {

		if (duration == null || Duration.ZERO.equals(duration)) {
			setTransform(affine);
		} else {
			setTransformAndAnimate(affine, duration, null);
		}

	}

	private Timeline animateSetTransform = null;

	/**
	 * Set the global transform to {@code affine} with an animation, over {@code duration} amount of time. When
	 * the animation is stopped, either due to it finishing, or being stopped early, {@code runAfterAnimation} will be triggered.
	 *
	 * The animation can be stopped early either by passing in a `duration` of `0` milliseconds or less, OR by setting `duration` to null.
	 * In both cases of the animation stopping early, the `runAfterrAnimation` will be triggered.
	 *
	 * @param affine to set the global transform to
	 * @param duration to animate the transform update over
	 * @param runAfterAnimation to run when the animation stops. This could either be when the global transform equals {@code affine} or if it was stopped early.
	 */
	public synchronized Timeline setTransformAndAnimate(
			final AffineTransform3D affine,
			@Nullable final Duration duration,
			@Nullable final Runnable runAfterAnimation) {

		if (duration == null || Duration.ZERO.equals(duration)) {
			setTransform(affine);
			if (runAfterAnimation != null)
				runAfterAnimation.run();
			return null;
		}
		final Timeline timeline = new Timeline(60.0);
		timeline.setCycleCount(1);
		timeline.setAutoReverse(false);
		final AffineTransform3D currentState = this.affine.copy();
		final DoubleProperty progressProperty = new SimpleDoubleProperty(0.0);
		final SimilarityTransformInterpolator interpolator = new SimilarityTransformInterpolator(currentState, affine.copy());
		progressProperty.addListener((obs, oldv, newv) -> setTransform(interpolator.get(newv.doubleValue())));
		final KeyValue kv = new KeyValue(progressProperty, 1.0, Interpolator.EASE_BOTH);
		timeline.getKeyFrames().add(new KeyFrame(duration, kv));
		if (runAfterAnimation != null)
			timeline.onFinishedProperty().set(t -> runAfterAnimation.run());
		timeline.play();
		return timeline;
	}

	public synchronized void setTransform(final AffineTransform3D affine) {

		resetTransformAnimation();
		this.affine.set(affine);
		notifyListeners();
	}

	private void resetTransformAnimation() {
		if (animateSetTransform != null) {
			animateSetTransform.stop();
			animateSetTransform = null;
		}
	}

	public void addListener(final TransformListener<AffineTransform3D> listener) {

		this.listeners.add(listener);
		listener.transformChanged(this.affine.copy());
	}

	public void removeListener(final TransformListener<AffineTransform3D> listener) {

		this.listeners.remove(listener);
	}

	public synchronized void preConcatenate(final AffineTransform3D transform) {

		this.affine.preConcatenate(transform);
		notifyListeners();
	}

	public synchronized void concatenate(final AffineTransform3D transform) {

		this.affine.concatenate(transform);
		notifyListeners();
	}

	private synchronized void notifyListeners() {

		for (final TransformListener<AffineTransform3D> l : listeners) {
			l.transformChanged(this.affine.copy());
		}
	}

	public void getTransform(final AffineTransform3D target) {

		target.set(this.affine);
	}

	public AffineTransform3D getTransform() {

		return this.affine.copy();
	}

}
