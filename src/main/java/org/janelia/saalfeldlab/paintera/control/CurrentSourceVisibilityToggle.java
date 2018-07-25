package org.janelia.saalfeldlab.paintera.control;

import java.util.Optional;

import javafx.beans.value.ObservableObjectValue;
import org.janelia.saalfeldlab.paintera.state.SourceState;

public class CurrentSourceVisibilityToggle
{

	private final ObservableObjectValue<SourceState<?, ?>> currentState;

	public CurrentSourceVisibilityToggle(final ObservableObjectValue<SourceState<?, ?>> currentState)
	{
		super();
		this.currentState = currentState;
	}

	public void setIsVisible(final boolean isVisible)
	{
		Optional.ofNullable(currentState.get()).ifPresent(state -> state.isVisibleProperty().set(isVisible));
	}

	public void toggleIsVisible()
	{
		final SourceState<?, ?> state = currentState.get();
		if (state != null)
		{
			state.isVisibleProperty().set(!state.isVisibleProperty().get());
		}
	}

}
