package org.janelia.saalfeldlab.paintera.state;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import net.imglib2.Volatile;
import net.imglib2.converter.ARGBCompositeColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.AbstractVolatileRealType;
import net.imglib2.view.composite.RealComposite;
import org.janelia.saalfeldlab.paintera.PainteraBaseView;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.data.ChannelDataSource;

@Deprecated
public class ChannelSourceState<
		D extends RealType<D>,
		T extends AbstractVolatileRealType<D, T>,
		CT extends RealComposite<T>,
		V extends Volatile<CT>>
		extends MinimalSourceState<RealComposite<D>,
		V,
		ChannelDataSource<RealComposite<D>, V>,
		ARGBCompositeColorConverter<T, CT, V>> {

  public ChannelSourceState(
		  final ChannelDataSource<RealComposite<D>, V> dataSource,
		  final ARGBCompositeColorConverter<T, CT, V> converter,
		  final Composite<ARGBType, ARGBType> composite,
		  final String name) {

	super(dataSource, converter, composite, name);
  }

  public long numChannels() {

	return getDataSource().numChannels();
  }

  @Override
  public void onAdd(final PainteraBaseView paintera) {

	for (int channel = 0; channel < numChannels(); ++channel) {
	  converter().colorProperty(channel).addListener((obs, oldv, newv) -> paintera.orthogonalViews().requestRepaint());
	  converter().minProperty(channel).addListener((obs, oldv, newv) -> paintera.orthogonalViews().requestRepaint());
	  converter().maxProperty(channel).addListener((obs, oldv, newv) -> paintera.orthogonalViews().requestRepaint());
	  converter().channelAlphaProperty(channel).addListener((obs, oldv, newv) -> paintera.orthogonalViews().requestRepaint());
	}
  }

  @Override
  public Node preferencePaneNode() {

	final Node node = super.preferencePaneNode();
	final VBox box = node instanceof VBox ? (VBox)node : new VBox(node);
	box.getChildren().add(new ChannelSourceStateConverterNode(this.converter()).getConverterNode());
	return box;
  }
}
