package org.janelia.saalfeldlab.fx.util;

import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;

public class OnSceneInitListener implements ChangeListener<Scene>
{

	private final Predicate<Scene> sceneCheck;

	private final Consumer<Scene> sceneConsumer;

	public OnSceneInitListener(final Consumer<Scene> sceneConsumer)
	{
		this(scene -> scene != null, sceneConsumer);
	}

	public OnSceneInitListener(final Predicate<Scene> sceneCheck, final Consumer<Scene> sceneConsumer)
	{
		super();
		this.sceneCheck = sceneCheck;
		this.sceneConsumer = sceneConsumer;
	}

	@Override
	public void changed(final ObservableValue<? extends Scene> observable, final Scene oldValue, final Scene newValue)
	{
		if (sceneCheck.test(newValue))
		{
			observable.removeListener(this);
			sceneConsumer.accept(newValue);
		}
	}

}
