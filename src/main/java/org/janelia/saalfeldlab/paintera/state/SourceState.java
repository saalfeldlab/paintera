package org.janelia.saalfeldlab.paintera.state;

import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.actions.ActionSet;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseBindings;
import org.janelia.saalfeldlab.paintera.control.modes.ControlMode;
import org.janelia.saalfeldlab.paintera.control.modes.NavigationControlMode;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;

public interface SourceState<D, T> {

	Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	DataSource<D, T> getDataSource();

	Converter<T, ARGBType> converter();

	ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty();

	StringProperty nameProperty();

	ReadOnlyStringProperty statusTextProperty();

	BooleanProperty isVisibleProperty();

	ObjectProperty<Interpolation> interpolationProperty();

	SourceState<?, ?>[] dependsOn();

	default Node getDisplayStatus() {

		return null;
	}

	default SourceAndConverter<T> getSourceAndConverter() {

		return new SourceAndConverter<>(getDataSource(), converter());
	}

	/**
	 * @return list of actions to install to the active viewer when this SourceState is active
	 */
	default List<ActionSet> getViewerActionSets() {

		LOG.trace("Default Viewer Action Sets; Not handling anything. ");
		return List.of();
	}

	/**
	 * @return @return list of actions to install to the application pane when this SourceState is active
	 */
	default List<ActionSet> getGlobalActionSets() {

		LOG.trace("Default Global Action Sets; Not handling anything. ");
		return List.of();
	}

	default void onAdd(PainteraBaseView paintera) {

		LOG.debug("Running default onAdd");
	}

	default void onRemoval(SourceInfo sourceInfo) {

		LOG.debug("Running default onRemoval");
	}

	default void onShutdown(PainteraBaseView paintera) {

		LOG.debug("Running default onShutdown");
	}

	default Node preferencePaneNode() {

		return defaultPreferencePaneNode(compositeProperty());
	}

	default KeyAndMouseBindings createKeyAndMouseBindings() {

		return new KeyAndMouseBindings();
	}

	default ControlMode getDefaultMode() {

		return NavigationControlMode.INSTANCE;
	}

	static VBox defaultPreferencePaneNode(ObjectProperty<Composite<ARGBType, ARGBType>> composite) {

		final TitledPane titledPane = SourceStateCompositePane.createTitledPane(composite);
		titledPane.minWidthProperty().set(0.0);
		final VBox vbox = new VBox(titledPane);
		vbox.setSpacing(0.0);
		vbox.setPadding(Insets.EMPTY);
		vbox.minWidthProperty().set(0.0);
		return vbox;
	}

}
