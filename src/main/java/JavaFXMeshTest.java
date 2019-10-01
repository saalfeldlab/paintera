import gnu.trove.set.hash.TLongHashSet;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.imglib2.Interval;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.fx.ortho.GridConstraintsManager;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.config.input.KeyAndMouseConfig;
import org.janelia.saalfeldlab.paintera.control.FitToInterval;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunctionAndCache;
import org.janelia.saalfeldlab.paintera.meshes.MeshGenerator;
import org.janelia.saalfeldlab.paintera.meshes.ShapeKey;
import org.janelia.saalfeldlab.paintera.meshes.cache.CacheUtils;
import org.janelia.saalfeldlab.paintera.meshes.cache.SegmentMaskGenerators;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.janelia.saalfeldlab.util.grids.LabelBlockLookupAllBlocks;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class JavaFXMeshTest extends Application {

	private final ExecutorService manager = Executors.newFixedThreadPool(3);
	private final ExecutorService workers = Executors.newFixedThreadPool(12);

	final long[] dims = {20, 30, 40};

	private final ArrayImg<UnsignedLongType, LongArray> data = ArrayImgs.unsignedLongs(dims);
	{
		final int[] blockSize = { 5, 7, 9 };
		final List<Interval> intervals = Grids.collectAllContainedIntervals(dims, blockSize);
		IntStream.range(0, intervals.size()).forEach(i -> Views.interval(data, intervals.get(i)).forEach(px -> px.setInteger(i)));
	}

	private final PainteraBaseView pbv = new PainteraBaseView(12, new KeyAndMouseConfig());

	private final Group root = new Group();

	private final LabelSourceState<UnsignedLongType, ?> source = LabelSourceState.simpleSourceFromSingleRAI(
			data,
			new double[]{1.0, 1.0, 1.0},
			new double[]{0.0, 0.0, 0.0},
			AxisOrder.XYZ,
			data.size(),
			"blub",
			new LabelBlockLookupAllBlocks(new long[][]{Intervals.dimensionsAsLongArray(data)}, new int[][]{{32, 32, 32}}),
			pbv.viewer3D().meshesGroup(), // using .root() instead of .meshesGroup() does not fix it
			manager,
			workers);

	final Function<CacheLoader<ShapeKey<TLongHashSet>, Pair<float[], float[]>>, Cache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>> makeCache = loader ->
			new SoftRefLoaderCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>().withLoader(loader);

	final InterruptibleFunctionAndCache<ShapeKey<TLongHashSet>, Pair<float[], float[]>>[] meshCaches = CacheUtils
			.segmentMeshCacheLoaders(source.getDataSource(), SegmentMaskGenerators.forType(source.getDataSource().getDataType()), makeCache);

	private final long[] ids = data.update(null).getCurrentStorageArray();

	@Override
	public void start(Stage primaryStage) {

		pbv
				.sourceInfo()
				.trackSources()
				.addListener(FitToInterval.fitToIntervalWhenSourceAddedListener(pbv.manager(), () -> pbv.orthogonalViews().topLeft().viewer().widthProperty().get()));

		pbv.orthogonalViews().grid().manage(new GridConstraintsManager());
		pbv.viewer3D().isMeshesEnabledProperty().set(true);
		pbv.pane().setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
		pbv.viewer3D().meshesGroup().getChildren().add(root);

		final Scene scene = new Scene(pbv.pane(), 800, 700);
		pbv.viewer3D().addEventFilter(MouseEvent.MOUSE_ENTERED, event -> pbv.viewer3D().requestFocus());
		primaryStage.setScene(scene);
		Platform.setImplicitExit(true);
		primaryStage.show();
		primaryStage.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
			if (!new KeyCodeCombination(KeyCode.SPACE).match(event))
				return;
			event.consume();
			if (true) {
				source.selectedIds().deactivateAll();
				source.selectedIds().activate(ids);
			} else {
				final List<MeshGenerator<TLongHashSet>> managers = LongStream
						.of(new TLongHashSet(ids).toArray())
						.mapToObj(this::createMeshManager)
						.collect(Collectors.toList());
				root.getChildren().setAll(managers.stream().map(MeshGenerator::getRoot).collect(Collectors.toList()));
			}
		});
		Platform.runLater(() -> {
			pbv.addState(source);
//			source.selectedIds().activate(data.update(null).getCurrentStorageArray());
			pbv.viewer3D().setInitialTransformToInterval(data);
		});
	}

	private MeshGenerator<TLongHashSet> createMeshManager(final long id) {
		return createMeshManager(new TLongHashSet(new long[] {id}));
	}

	private MeshGenerator<TLongHashSet> createMeshManager(final TLongHashSet fragments) {

		final Function<TLongHashSet, Interval[]> func = id ->
				new LabelBlockLookupAllBlocks(new long[][]{Intervals.dimensionsAsLongArray(data)}, new int[][]{{32, 32, 32}})
						.read(0, id.iterator().next());

		return new MeshGenerator<TLongHashSet>(
				fragments,//new TLongHashSet(new long[] {1}),
				new InterruptibleFunction[] { InterruptibleFunction.fromFunction(func) },
				meshCaches,
				new SimpleIntegerProperty(0xffffffff),
				0,
				3,
				0.5,
				3,
				manager,
				workers);

	}

}
