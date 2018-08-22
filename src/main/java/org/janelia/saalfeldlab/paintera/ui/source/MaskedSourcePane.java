package org.janelia.saalfeldlab.paintera.ui.source;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

public class MaskedSourcePane implements BindUnbindAndNodeSupplier
{

	private final MaskedSource<?, ?> maskedSource;

	public MaskedSourcePane(MaskedSource<?, ?> maskedSource)
	{
		this.maskedSource = maskedSource;
	}

	@Override
	public Node get()
	{
		final CheckBox showCanvasCheckBox = new CheckBox("Show Canvas");
		showCanvasCheckBox.selectedProperty().bindBidirectional(maskedSource.showCanvasOverBackgroundProperty());
		VBox contents = new VBox(showCanvasCheckBox);
		return TitledPanes.createCollapsed("Canvas", contents);
	}

	@Override
	public void bind()
	{

	}

	@Override
	public void unbind()
	{

	}
}
