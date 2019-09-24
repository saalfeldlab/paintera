package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import javafx.application.Platform;
import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Tooltip;

public class MeshProgressBar extends StatusBar
{
	private final IntegerProperty numTasks = new SimpleIntegerProperty(0);
	private final IntegerProperty numCompletedTasks = new SimpleIntegerProperty(0);

	public MeshProgressBar()
	{
		setStyle("-fx-accent: green; ");
		final Tooltip statusToolTip = new Tooltip();
		setTooltip(statusToolTip);

		final Runnable progressUpdater = () -> {
			assert Platform.isFxApplicationThread();

			final int numTasksVal = numTasks.get();
			final int numCompletedTasksVal = numCompletedTasks.get();

			if (numCompletedTasksVal >= numTasksVal)
				setProgress(0.0); // hides the progress bar to indicate that there are no pending tasks
			else if (numCompletedTasksVal <= 0)
				setProgress(1e-7); // displays an empty progress bar
			else
				setProgress((double) numCompletedTasksVal / numTasksVal);

			statusToolTip.setText(numCompletedTasksVal + "/" + numTasksVal);
		};

		numTasks.addListener(obs -> progressUpdater.run());
		numCompletedTasks.addListener(obs -> progressUpdater.run());
	}

	public IntegerProperty numTasksProperty()
	{
		return numTasks;
	}

	public IntegerProperty numCompletedTasksProperty()
	{
		return numCompletedTasks;
	}
}
