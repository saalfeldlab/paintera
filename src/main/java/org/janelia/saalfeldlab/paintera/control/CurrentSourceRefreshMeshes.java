package org.janelia.saalfeldlab.paintera.control;

import java.util.Optional;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.paintera.state.HasMeshes;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;

public class CurrentSourceRefreshMeshes
{

	private final Supplier<SourceState<?, ?>> currentState;

	public CurrentSourceRefreshMeshes(final Supplier<SourceState<?, ?>> currentState)
	{
		super();
		this.currentState = currentState;
	}

	public void refresh()
	{
		Optional
				.ofNullable(currentState.get())
				.filter(state -> state instanceof HasMeshes<?>)
				.map(state -> (HasMeshes<?>) state)
				.ifPresent(this::refresh);
	}

	private void refresh(final HasMeshes<?> hasMeshes)
	{
		hasMeshes.refreshMeshes();
	}

}
