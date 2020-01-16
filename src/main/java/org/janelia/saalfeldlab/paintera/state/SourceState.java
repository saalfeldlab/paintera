package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public interface SourceState<D, T>
{

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	DataSource<D, T> getDataSource();

	Converter<T, ARGBType> converter();

	ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty();

	StringProperty nameProperty();

	ReadOnlyStringProperty statusTextProperty();

	BooleanProperty isVisibleProperty();

	ObjectProperty<Interpolation> interpolationProperty();

	SourceState<?, ?>[] dependsOn();

	Node getDisplayStatus();

	default SourceAndConverter<T> getSourceAndConverter()
	{
		return new SourceAndConverter<>(getDataSource(), converter());
	}

	default EventHandler<Event> stateSpecificGlobalEventHandler(PainteraBaseView paintera, KeyTracker keyTracker) {
		return e -> {
			LOG.debug("Default state specific event handler: Not handling anything");
		};
	}

	default EventHandler<Event> stateSpecificGlobalEventFilter(PainteraBaseView paintera, KeyTracker keyTracker) {
		return e -> {
			LOG.debug("Default state specific event filter: Not handling anything");
		};
	}

	default EventHandler<Event> stateSpecificViewerEventHandler(PainteraBaseView paintera, KeyTracker keyTracker) {
		return e -> {
			LOG.debug("Default state specific viewer event handler: Not handling anything");
		};
	}

	default EventHandler<Event> stateSpecificViewerEventFilter(PainteraBaseView paintera, KeyTracker keyTracker) {
		return e -> {
			LOG.debug("Default state specific viewer event filter: Not handling anything");
		};
	}

	default void onAdd(PainteraBaseView paintera) {
		LOG.debug("Running default onAdd");
	}

	default void onRemoval(SourceInfo paintera) {
		LOG.debug("Running default onRemoval");
	}

	default void onShutdown(PainteraBaseView paintera) {
		LOG.debug("Running default onShutdown");
	}

	default Node preferencePaneNode() {
		return defaultPreferencePaneNode(compositeProperty());
	}

	KeyAndMouseBindings createKeyAndMouseBindings();

	static VBox defaultPreferencePaneNode(ObjectProperty<Composite<ARGBType, ARGBType>> composite) {
		final TitledPane titledPane = SourceStateCompositePane.createTitledPane(composite);
		final VBox vbox = new VBox(titledPane);
		vbox.setSpacing(0.0);
		vbox.setPadding(Insets.EMPTY);
		return vbox;
	}

}
