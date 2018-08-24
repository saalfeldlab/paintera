package org.janelia.saalfeldlab.paintera;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.imglib2.converter.ARGBColorConverter;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5ChannelDataSource;
import org.janelia.saalfeldlab.paintera.data.n5.N5HDF5Meta;
import org.janelia.saalfeldlab.paintera.data.n5.VolatileWithSet;
import org.janelia.saalfeldlab.paintera.state.ChannelSourceState;
import org.janelia.saalfeldlab.paintera.state.RawSourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class PainteraTestMultiChannel extends Application {

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void main(String[] args)
	{
		Application.launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		final String path = "/groups/saalfeld/home/hanslovskyp/workspace-pycharm/u-net/test-aff/snapshots/batch_22251.hdf";
		final String raw = "volumes/raw";
		final String dataset = "volumes/affinities/gt";
		N5HDF5Meta meta = new N5HDF5Meta(path, dataset, new int[] {64, 64, 64, 3}, true);
		final AffineTransform3D transform = N5Helpers.getTransform(meta.reader(), meta.dataset(), true);
		final PainteraBaseView.DefaultPainteraBaseView viewer = PainteraBaseView.defaultView();
		N5ChannelDataSource<FloatType, VolatileFloatType> source = N5ChannelDataSource.zeroExtended(
				meta,
				transform,
				viewer.baseView.getQueue(),
				"channels",
				0,
				3);

		final long numChannels = source.numChannels();
		LOG.info("num channels: {}", numChannels);


		DataSource<FloatType, VolatileFloatType> rawSource = N5Helpers.openRawAsSource(
				meta.reader(),
				raw,
				N5Helpers.getTransform(meta.reader(), raw, true), viewer.baseView.getQueue(), 0, "raw");
		RawSourceState<FloatType, VolatileFloatType> rawState = new RawSourceState<>(
				rawSource,
				new ARGBColorConverter.Imp0<>(),
				new CompositeCopy<>(),
				rawSource.getName()
		);
		rawState.converter().setMin(0.0);
		rawState.converter().setMax(1.0);

		ARGBCompositeColorConverter<VolatileFloatType, RealComposite<VolatileFloatType>, VolatileWithSet<RealComposite<VolatileFloatType>>> conv =
				ARGBCompositeColorConverter.imp0((int) numChannels, 0.0, 1.0);
		final ChannelSourceState<FloatType, VolatileFloatType, VolatileWithSet<RealComposite<VolatileFloatType>>> state = new ChannelSourceState<>(
				source,
				conv,
				new ARGBCompositeAlphaAdd(),
				"channels"
		);

		for (int channel = 0; channel < numChannels; ++channel) {
			conv.minProperty(0).set(0.0);
			conv.maxProperty(0).set(1.0);
		}

		viewer.baseView.addState(rawState);
		viewer.baseView.addState(state);

		final Scene scene = new Scene(viewer.paneWithStatus.getPane());
		viewer.keyTracker.installInto(scene);
		primaryStage.setScene(scene);
		primaryStage.show();
		viewer.baseView.orthogonalViews().requestRepaint();
		Platform.setImplicitExit(true);
		primaryStage.addEventFilter(WindowEvent.WINDOW_HIDDEN, e -> viewer.baseView.stop());
	}
}
