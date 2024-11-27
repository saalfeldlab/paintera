package org.janelia.saalfeldlab.paintera.ui.dialogs.open;

import javafx.beans.value.ObservableValue;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface CombinesErrorMessages {

	Collection<ObservableValue<String>> errorMessages();

	Consumer<Collection<String>> combiner();

	default void combineErrorMessages() {

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
