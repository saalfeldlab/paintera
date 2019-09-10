package org.janelia.saalfeldlab.paintera.control;

import java.util.Optional;
import java.util.function.Supplier;

import bdv.viewer.Source;
import gnu.trove.map.hash.TObjectIntHashMap;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.janelia.saalfeldlab.paintera.state.HasHighlightingStreamConverter;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.janelia.saalfeldlab.paintera.stream.AbstractHighlightingARGBStream;

@Deprecated
public class ShowOnlySelectedInStreamToggle
{

	private final Supplier<SourceState<?, ?>> currentState;

	private final TObjectIntHashMap<Source<?>> alphaMemory = new TObjectIntHashMap<>();

	public ShowOnlySelectedInStreamToggle(
			final Supplier<SourceState<?, ?>> currentState,
			final ObservableList<Source<?>> removedSources
	                                     )
	{
		super();
		this.currentState = currentState;
		removedSources.addListener((ListChangeListener<Source<?>>) change -> {
			while (change.next())
			{
				if (change.wasRemoved())
				{
					change.getRemoved().forEach(alphaMemory::remove);
				}
			}
		});
	}

	public void toggleNonSelectionVisibility()
	{
		Optional
				.ofNullable(currentState.get())
				.filter(state -> state instanceof HasHighlightingStreamConverter<?>)
				.ifPresent(state -> this.toggleNonSelectionVisibility(state.getDataSource(), ((HasHighlightingStreamConverter<?>)state).highlightingStreamConverter().getStream()));
	}

	private void toggleNonSelectionVisibility(Source<?> source, AbstractHighlightingARGBStream stream)
	{
		if (alphaMemory.contains(source))
		{
			stream.setAlpha(alphaMemory.remove(source));
		}
		else
		{
			alphaMemory.put(source, stream.getAlpha());
			stream.setAlpha(0);
		}
	}


}
