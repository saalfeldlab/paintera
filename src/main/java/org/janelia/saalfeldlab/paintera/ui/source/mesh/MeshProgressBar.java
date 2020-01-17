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

						if (numCompletedTasks >= numTasks)
							setProgress(0.0); // hides the progress bar to indicate that there are no pending tasks
						else if (numCompletedTasks <= 0)
							setProgress(1e-7); // displays an empty progress bar
						else
							setProgress((double) numCompletedTasks / numTasks);

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
}
