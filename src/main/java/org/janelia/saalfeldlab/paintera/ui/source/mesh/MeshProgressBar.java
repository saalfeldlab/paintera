package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import io.reactivex.disposables.Disposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import javafx.scene.control.Tooltip;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.paintera.meshes.ObservableMeshProgress;

import java.util.concurrent.TimeUnit;

public class MeshProgressBar extends StatusBar
{
	public static final long UPDATE_INTERVAL_MSEC = 100;

	private final Tooltip statusToolTip = new Tooltip();

	private final long updateIntervalMsec;

	private ObservableMeshProgress meshProgress;

	private Disposable disposable;

	public MeshProgressBar()
	{
		this(UPDATE_INTERVAL_MSEC);
	}

	public MeshProgressBar(final long updateIntervalMsec)
	{
		this.updateIntervalMsec = updateIntervalMsec;
		setStyle("-fx-accent: green; ");
		setTooltip(statusToolTip);
	}

	public void bindTo(final ObservableMeshProgress meshProgress)
	{
		unbind();
		this.meshProgress = meshProgress;
		if (this.meshProgress != null)
		{
			this.disposable = JavaFxObservable
					.invalidationsOf(this.meshProgress)
					.throttleLast(updateIntervalMsec, TimeUnit.MILLISECONDS)
					.observeOn(JavaFxScheduler.platform())
					.subscribe(val -> {
						final int numTasks = meshProgress.getNumTasks();
						final int numCompletedTasks = meshProgress.getNumCompletedTasks();

						if (numTasks == 0)
							setProgress(0.0); // hide progress bar when there is nothing to do
						else if (numCompletedTasks <= 0)
							setProgress(1e-7); // displays an empty progress bar
						else if (numCompletedTasks >= numTasks) {
							setStyle(ProgressStyle.FINISHED);
							setProgress(1.0);
						}
						else {
							setStyle(ProgressStyle.IN_PROGRESS);
							setProgress((double) numCompletedTasks / numTasks);
						}

						statusToolTip.setText(numCompletedTasks + "/" + numTasks);
					});
		}
	}

	public void unbind()
	{
		if (this.meshProgress != null)
		{
			this.disposable.dispose();
			this.disposable = null;
			this.meshProgress = null;
		}
	}

	private static class ProgressStyle {
		// color combination inspired by
		// https://www.designwizard.com/blog/design-trends/colour-combination
		// pacific coast (finished) and living coral
		private static final String COLOR_FINISHED = "#5B84B1FF";
		private static final String COLOR_IN_PROGRESS = "#FC766AFF";

		private static String getStyle(final String color) {
			return String.format("-fx-accent: %s; ", color);
		}

		public static final String IN_PROGRESS = getStyle(COLOR_IN_PROGRESS);
		public static final String FINISHED = getStyle(COLOR_FINISHED);
	}


}
