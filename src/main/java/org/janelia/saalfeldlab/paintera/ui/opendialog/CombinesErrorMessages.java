package org.janelia.saalfeldlab.paintera.ui.opendialog;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.beans.value.ObservableValue;

public interface CombinesErrorMessages
{

	public Collection<ObservableValue<String>> errorMessages();

	public Consumer<Collection<String>> combiner();

	public default void combineErrorMessages()
	{
		combiner().accept(errorMessages()
				.stream()
				.map(ObservableValue::getValue)
				.map(Optional::ofNullable)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(s -> s.length() > 0)
				.collect(Collectors.toList()));
	}

}
