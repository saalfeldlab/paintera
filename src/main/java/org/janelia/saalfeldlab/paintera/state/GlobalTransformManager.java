package org.janelia.saalfeldlab.paintera.state;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.transform.Affine;
import javafx.util.Duration;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.TransformListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GlobalTransformManager
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final ArrayList<TransformListener<AffineTransform3D>> listeners;

	private final AffineTransform3D affine;

	@SafeVarargs
	public GlobalTransformManager(final TransformListener<AffineTransform3D>... listeners)
	{
		this(new AffineTransform3D(), listeners);
	}

	@SafeVarargs
	public GlobalTransformManager(final AffineTransform3D affine, final TransformListener<AffineTransform3D>...
			listeners)
	{
		this(affine, Arrays.asList(listeners));
	}

	public GlobalTransformManager(final AffineTransform3D affine, final List<TransformListener<AffineTransform3D>>
			listeners)
	{
		super();
		this.listeners = new ArrayList<>();
		this.affine = affine;
		listeners.forEach(this::addListener);
	}

	public synchronized void setTransform(final AffineTransform3D affine, final Duration duration) {
		if (duration.toMillis() == 0.0) {
			setTransform(affine);
			return;
		}
		final Timeline timeline = new Timeline(60.0);
		timeline.setCycleCount(1);
		timeline.setAutoReverse(false);
		final AffineTransform3D currentState = this.affine.copy();
		final DoubleProperty progressProperty = new SimpleDoubleProperty(0.0);
		final AffineTransform3D progress = new AffineTransform3D();
		progressProperty.addListener((obs, oldv, newv) -> {
			final double w2 = newv.doubleValue();
			final double w1 = 1.0 - w2;
			for (int row = 0; row < 3; ++row)
				for (int col = 0; col < 4; ++col)
					progress.set(w1 * currentState.get(row, col) + w2 * affine.get(row, col), row, col);
			setTransform(progress);
		});
		final KeyValue kv = new KeyValue(progressProperty, 1.0, Interpolator.LINEAR);
		timeline.getKeyFrames().add(new KeyFrame(duration, kv));
		timeline.play();
	}

	public synchronized void setTransform(final AffineTransform3D affine)
	{
		this.affine.set(affine);
		notifyListeners();
	}

	public void addListener(final TransformListener<AffineTransform3D> listener)
	{
		this.listeners.add(listener);
		listener.transformChanged(this.affine.copy());
	}

	public void removeListener(final TransformListener<AffineTransform3D> listener)
	{
		this.listeners.remove(listener);
	}

	public synchronized void preConcatenate(final AffineTransform3D transform)
	{
		this.affine.preConcatenate(transform);
		notifyListeners();
	}

	public synchronized void concatenate(final AffineTransform3D transform)
	{
		this.affine.concatenate(transform);
		notifyListeners();
	}

	private synchronized void notifyListeners()
	{
		for (final TransformListener<AffineTransform3D> l : listeners)
			l.transformChanged(this.affine.copy());
	}

	public void getTransform(final AffineTransform3D target)
	{
		target.set(this.affine);
	}

	private static Affine fromAffineTransform3D(final AffineTransform3D affineTransform3D) {
		return new Affine(
				affineTransform3D.get(0, 0), affineTransform3D.get(0, 1), affineTransform3D.get(0, 2), affineTransform3D.get(0, 3),
				affineTransform3D.get(1, 0), affineTransform3D.get(1, 1), affineTransform3D.get(1, 2), affineTransform3D.get(1, 3),
				affineTransform3D.get(2, 0), affineTransform3D.get(2, 1), affineTransform3D.get(2, 2), affineTransform3D.get(2, 3));
	};

	private static AffineTransform3D fromAffine(final Affine affine) {
		final AffineTransform3D affineTransform3D = new AffineTransform3D();
		affineTransform3D.set(
				affine.getMxx(), affine.getMxy(), affine.getMxz(), affine.getTx(),
				affine.getMyx(), affine.getMyy(), affine.getMyz(), affine.getTy(),
				affine.getMzx(), affine.getMzy(), affine.getMzz(), affine.getTz());
		return affineTransform3D;
	}

}
