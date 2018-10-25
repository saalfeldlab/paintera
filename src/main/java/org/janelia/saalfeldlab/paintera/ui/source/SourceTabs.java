package org.janelia.saalfeldlab.paintera.ui.source;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.ui.source.state.StatePane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceTabs implements Supplier<Node>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final VBox contents = new VBox();

	{
		contents.setSpacing(0);
		contents.setMaxHeight(Double.MAX_VALUE);
	}

	private final ScrollPane sp = new ScrollPane(contents);

	{
		sp.setMaxWidth(Double.MAX_VALUE);
		sp.setHbarPolicy(ScrollBarPolicy.NEVER);
		sp.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
	}

	private final HashMap<Source<?>, StatePane> statePaneCache = new HashMap<>();

	private final ObservableList<StatePane> statePanes = FXCollections.observableArrayList();

	{
		statePanes.addListener((ListChangeListener<StatePane>) change -> InvokeOnJavaFXApplicationThread.invoke(() ->
				this.contents.getChildren().setAll(
				statePanes.stream().map(StatePane::get).collect(Collectors.toList()))));
	}

	private final SourceInfo info;

	private final DoubleProperty width = new SimpleDoubleProperty();

	private final Consumer<Source<?>> remove;

	public SourceTabs(
			final ObservableIntegerValue currentSourceIndex,
			final Consumer<Source<?>> remove,
			final SourceInfo info)
	{
		LOG.debug("Constructing {}", SourceTabs.class.getName());
		this.info = info;
		this.remove = remove;
		width.set(300);
		this.info.trackSources().addListener((ListChangeListener<Source<?>>) change -> {
			final ArrayList<Source<?>> copy = new ArrayList<>(this.info.trackSources());
			final List<StatePane> show = copy.stream().map(source -> statePaneCache.computeIfAbsent(
					source,
					this::makeStatePane)).collect(Collectors.toList());
			new ArrayList<>(this.statePanes).forEach(StatePane::unbind);
			show.forEach(StatePane::bind);
			this.statePanes.setAll(show);
		});

		this.info.removedSourcesTracker().addListener((ListChangeListener<Source<?>>) change -> {
			final ArrayList<? extends Source<?>> list = new ArrayList<>(change.getList());
			list
					.stream()
					.map(statePaneCache::remove)
					.map(Optional::ofNullable)
					.forEach(o -> o.ifPresent(StatePane::unbind));
		});

	}

	@Override
	public Node get()
	{
		return sp;
	}

	private static void removeDialog(final Consumer<Source<?>> onRemove, final Source<?> source)
	{
		final Alert confirmRemoval = new Alert(
				Alert.AlertType.CONFIRMATION,
				String.format("Remove source '%s'?", source.getName())
		);
		final Button removeButton = (Button) confirmRemoval.getDialogPane().lookupButton(
				ButtonType.OK);
		removeButton.setText("Remove");
		confirmRemoval.setHeaderText(null);
		confirmRemoval.setTitle(null);
		confirmRemoval.initModality(Modality.APPLICATION_MODAL);
		final Optional<ButtonType> buttonClicked = confirmRemoval.showAndWait();
		if (buttonClicked.orElse(ButtonType.CANCEL).equals(ButtonType.OK))
		{
			onRemove.accept(source);
		}
	}

	public DoubleProperty widthProperty()
	{
		return this.width;
	}

	private StatePane makeStatePane(final Source<?> source)
	{
		final StatePane p = new StatePane(
				info.getState(source),
				info,
				s -> removeDialog(remove, s),
				width);
		addDragAndDropListener(p.get(), info, contents.getChildren());
		return p;
	}

	private static void addDragAndDropListener(final Node p, final SourceInfo info, final List<Node> children)
	{
		p.setOnDragDetected( event -> {
			p.startFullDrag();
		} );

		p.setOnMouseDragReleased( event -> {
			final Object origin = event.getGestureSource();
			if ( origin != p && origin instanceof TitledPane)
			{
				final TitledPane pane = ( TitledPane ) origin;
				final int sourceIndex = children.indexOf( pane );
				final int targetIndex = children.indexOf( p );
				info.moveSourceTo( sourceIndex, targetIndex );
			}
		} );
	}

}
