package org.janelia.saalfeldlab.paintera.ui.dialogs.open;

import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

public interface BackendDialog {

	Node getDialogNode();

	ObservableValue<String> errorMessage();

	DoubleProperty[] resolution();

	default void setResolution(final double[] resolution) {

		final DoubleProperty[] res = resolution();
		for (int i = 0; i < res.length; ++i) {
			res[i].set(resolution[i]);
		}
	}

	DoubleProperty[] offset();

	default void setOffset(final double[] offset) {

		final DoubleProperty[] off = offset();
		for (int i = 0; i < off.length; ++i) {
			off[i].set(offset[i]);
		}
	}

	DoubleProperty min();

	DoubleProperty max();

	ObservableStringValue nameProperty();

	String identifier();

	default Object metaData() {

		return null;
	}

}
