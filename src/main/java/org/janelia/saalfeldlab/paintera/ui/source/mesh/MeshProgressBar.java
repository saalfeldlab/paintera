package org.janelia.saalfeldlab.paintera.ui.source.mesh;

import org.controlsfx.control.StatusBar;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Tooltip;

public class MeshProgressBar extends StatusBar
{
	private final IntegerProperty numPendingTasks = new SimpleIntegerProperty(0);

	private final IntegerProperty numCompletedTasks = new SimpleIntegerProperty(0);

	public MeshProgressBar()
	{
		setStyle("-fx-accent: green; ");
		final Tooltip statusToolTip = new Tooltip();
		setTooltip(statusToolTip);

		final Runnable progressUpdater = () -> {
			final int numPendingTasksVal = numPendingTasks.get();
			final int numCompletedTasksVal = numCompletedTasks.get();
			InvokeOnJavaFXApplicationThread.invoke(() -> {
				if (numPendingTasksVal <= 0)
					setProgress(0.0); // hides the progress bar to indicate that there are no pending tasks
				else if (numCompletedTasksVal <= 0)
					setProgress(1e-7); // displays an empty progress bar
				else
					setProgress(calculateProgress(numPendingTasksVal, numCompletedTasksVal));

				statusToolTip.setText(statusBarToolTipText(numPendingTasksVal, numCompletedTasksVal));
			});
		};

		numPendingTasks.addListener(obs -> progressUpdater.run());
		numCompletedTasks.addListener(obs -> progressUpdater.run());
	}

	public IntegerProperty numPendingTasksProperty()
	{
		return numPendingTasks;
	}

	public IntegerProperty numCompletedTasksProperty()
	{
		return numCompletedTasks;
	}

	private static double calculateProgress(final int pendingTasks, final int completedTasks)
	{
		return (double) completedTasks / (pendingTasks + completedTasks);
	}

	private static String statusBarToolTipText(final int pendingTasks, final int completedTasks)
	{
		return completedTasks + "/" + (pendingTasks + completedTasks);
	}
}
