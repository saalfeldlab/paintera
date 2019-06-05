package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.event.KeyTracker;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.HasModifiableAxisOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public interface SourceState<D, T> extends HasModifiableAxisOrder
{

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	DataSource<D, T> getDataSource();

	Converter<T, ARGBType> converter();

	ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty();

	StringProperty nameProperty();

	StringProperty statusTextProperty();

	BooleanProperty isVisibleProperty();

	ObservableBooleanValue isDirtyProperty();

	ObjectProperty<Interpolation> interpolationProperty();

	SourceState<?, ?>[] dependsOn();

	void stain();

	void clean();

	boolean isDirty();

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

	}

//	default void onRemove(PainteraBaseView paintera) {
//
//	}

}
