package bdv.bigcat.viewer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ochafik.lang.jnaerator.runtime.This;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.viewer.ViewerNode.ViewerAxis;
import bdv.bigcat.viewer.source.LabelLayer;
import bdv.bigcat.viewer.source.LabelSource;
import bdv.bigcat.viewer.source.RawLayer;
import bdv.bigcat.viewer.source.Source;
import bdv.bigcat.viewer.source.SourceLayer;
import bdv.cache.CacheControl;
import bdv.img.h5.H5LabelMultisetSetupImageLoader;
import bdv.labels.labelset.LabelMultisetType;
import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import cleargl.GLVector;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Hub;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryElement;
import graphics.scenery.Settings;
import graphics.scenery.backends.Renderer;
import graphics.scenery.utils.SceneryPanel;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingNode;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

public class OrthoView {
	// private Node infoPane;
	private Viewer3D viewer3D;

	private final ViewerNode[] viewerNodes = new ViewerNode[3];

	private final ViewerTransformManager[] managers = new ViewerTransformManager[3];

	private GridPane grid;

	private boolean createdViewers;

	private boolean created3DViewer;

	final boolean[] isFullScreen = new boolean[] { false };

	final HashMap<bdv.viewer.Source<?>, Composite<ARGBType, ARGBType>> sourceCompositeMap = new HashMap<>();

	final ObservableList<SourceLayer> sourceLayers = FXCollections.observableArrayList();
	{
		sourceLayers.addListener((ListChangeListener<SourceLayer>) c -> {
			c.next();
			if (c.wasRemoved())
				c.getRemoved().forEach(sourceCompositeMap::remove);

		});
	}

	public OrthoView() {
		viewer3D = new Viewer3D();
//		addViewerNodesHandlers(this.viewerNodes);
	}

	public synchronized boolean isInitialized() {
		return createdViewers;
	}

	public SourceLayer addSource(final Source<?, ?> source) throws Exception {
		waitUntilInitialized();

		if (sourceLayers.stream().map(SourceLayer::name).filter(source::equals).count() > 0)
			return null;

		final SourceLayer sourceLayer;
		if (source instanceof LabelSource)
			sourceLayer = new LabelLayer((LabelSource) source, sourceCompositeMap);
		else
			sourceLayer = new RawLayer<>((Source) source);

		this.sourceLayers.add(sourceLayer);

		return sourceLayer;

	}

	public void removeSource(final Source<?, ?> source) {
		waitUntilInitialized();
		final Stream<?> matches = this.sourceLayers.stream().filter(sourceLayer -> sourceLayer.source().equals(source));
		matches.forEach(sourceLayer -> {
			this.sourceLayers.remove(sourceLayer);
		});
	}

	private void waitUntilInitialized() {
		while (!isInitialized())
			try {
				Thread.sleep(10);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				return;
			}
	}

	private static GridPane createGrid( Viewer3D viewer3D ) {

		final GridPane grid = new GridPane();
		
		GridPane pane = new GridPane();
		GridPane.setHgrow(viewer3D.getPanel(), Priority.ALWAYS);
		GridPane.setVgrow(viewer3D.getPanel(), Priority.ALWAYS);

		GridPane.setFillHeight(viewer3D.getPanel(), true);
		GridPane.setFillWidth(viewer3D.getPanel(), true);

		grid.setHgap(1);
		grid.setVgap(1);
		
		grid.setStyle("-fx-background-color: rgb(20, 255, 20);"
				+ "-fx-font-family: Consolas;"
				+ "-fx-font-weight: 400;"
				+ "-fx-font-size: 1.2em;"
				+ "-fx-text-fill: white;"
				+ "-fx-text-alignment: center;");

		grid.add(viewer3D.getPanel(), 0, 0);
		return grid;
	}

	private void create3DView() {

		viewer3D.createViewer3D();

		final Thread t = new Thread(() -> {
			while (!created3DViewer) {
				try {
					Thread.sleep(10);
				} catch (final InterruptedException e) {
					e.printStackTrace();
					return;
				}
				created3DViewer = viewer3D.isReady();
			}
			created3DViewer = true;
		});
		t.start();

		
	}

	private static void addViewerNodesHandlers(final ViewerNode[] viewerNodesArray) {
		final Thread t = new Thread(() -> {
			while (viewerNodesArray[0] == null || viewerNodesArray[1] == null || viewerNodesArray[2] == null)
				try {
					Thread.sleep(10);
				} catch (final InterruptedException e) {
					e.printStackTrace();
					return;
				}
			final Class<?>[] focusKeepers = { TextField.class };
			for (int i = 0; i < viewerNodesArray.length; ++i) {
				final SwingNode viewerNode = viewerNodesArray[i];
				// final DropShadow ds = new DropShadow( 10, Color.PURPLE );
				final DropShadow ds = new DropShadow(10,
						Color.hsb(60.0 + 360.0 * i / viewerNodesArray.length, 1.0, 0.5, 1.0));
				viewerNode.focusedProperty().addListener((ChangeListener<Boolean>) (observable, oldValue, newValue) -> {
					if (newValue)
						viewerNode.setEffect(ds);
					else
						viewerNode.setEffect(null);
				});
			}
		});
		t.start();
	}

	public void start(final Stage primaryStage) throws Exception {

		System.out.println("stage: " + primaryStage );
		primaryStage.setTitle("BigCAT");

		this.grid = createGrid( viewer3D);

		final Scene scene = new Scene(grid, 500, 500);
		primaryStage.setScene(scene);
		primaryStage.setOnCloseRequest( new EventHandler<WindowEvent>(){

			@Override
			public void handle(WindowEvent event) {
				viewer3D.getRenderer().setShouldClose(true);
				
				Platform.runLater( new Runnable() {
					
					@Override
					public void run() {
						Platform.exit();
					}
				});
			}
		});
		primaryStage.show();
		
		create3DView();
	}
}