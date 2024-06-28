package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.animation.AnimationTimer;
import javafx.css.PseudoClass;
import javafx.scene.control.Tooltip;
import javafx.util.Subscription;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.paintera.meshes.ObservableMeshProgress;


public class MeshProgressBar extends StatusBar {

	public static final long UPDATE_INTERVAL_MSEC = 100;

	private final Tooltip statusToolTip = new Tooltip();

	private final long updateIntervalMsec;

	private ObservableMeshProgress meshProgress;

	private AnimationTimer progressBarUpdater;

	public MeshProgressBar() {

		this(UPDATE_INTERVAL_MSEC);
	}

	public MeshProgressBar(final long updateIntervalMsec) {

		this.updateIntervalMsec = updateIntervalMsec;
		setTooltip(statusToolTip);

		setCssProperties();
	}

	private void setCssProperties() {

		getStyleClass().add("mesh-status-bar");
		final PseudoClass complete = PseudoClass.getPseudoClass("complete");
		progressProperty().subscribe(progress -> pseudoClassStateChanged(complete, !(progress.doubleValue() < 1.0)));
	}

	public void bindTo(final ObservableMeshProgress meshProgress) {

		unbind();
		this.meshProgress = meshProgress;
		if (this.meshProgress == null) return;

		progressBarUpdater = createAnimationTimer(meshProgress);
		progressBarUpdater.start();

	}

	private AnimationTimer createAnimationTimer(ObservableMeshProgress meshProgress) {

		return new AnimationTimer() {

			private Subscription subscription;
			long lastUpdate = -1L;
			boolean handleUpdate = false;

			@Override public void start() {

				super.start();
				this.subscription = meshProgress.subscribe(() -> handleUpdate = true);
			}

			@Override public void stop() {

				super.stop();
				if (subscription!= null)
					subscription.unsubscribe();
			}

			@Override public void handle(long now) {
				if (handleUpdate && now - lastUpdate > updateIntervalMsec) {
					lastUpdate = now;
					final int numTasks = meshProgress.getNumTasks();
					final int numCompletedTasks = meshProgress.getNumCompletedTasks();

					if (numTasks == 0)
						setProgress(0.0); // hide progress bar when there is nothing to do
					else if (numCompletedTasks <= 0)
						setProgress(1e-7); // displays an empty progress bar
					else if (numCompletedTasks >= numTasks) {
						setProgress(1.0);
					} else {
						setProgress((double)numCompletedTasks / numTasks);
					}

					statusToolTip.setText(numCompletedTasks + "/" + numTasks);
				}
			}
		};
	}

	public void unbind() {

		if (progressBarUpdater != null)
			progressBarUpdater.stop();

		if (meshProgress != null)
			meshProgress = null;

		setProgress(1e-7);
	}
}
