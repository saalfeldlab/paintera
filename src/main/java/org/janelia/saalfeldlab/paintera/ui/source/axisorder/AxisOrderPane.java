package org.janelia.saalfeldlab.paintera.ui.source.axisorder;

import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import net.imglib2.type.numeric.ARGBType;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.data.axisorder.AxisOrder;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

public class AxisOrderPane implements BindUnbindAndNodeSupplier
{

	private static final ObservableList<AxisOrder> AVAILABLE_AXIS_ORDERS = FXCollections.observableArrayList(AxisOrder.spatialValues());

	private final ComboBox combo = new ComboBox<>(AVAILABLE_AXIS_ORDERS);

	private final ObjectProperty<AxisOrder> axisOrder;

	private final Node n = createNode();

	public AxisOrderPane(final ObjectProperty<AxisOrder> axisOrder)
	{
		super();
		this.axisOrder = axisOrder;
	}


	@Override
	public Node get()
	{
		return n;
	}

	@Override
	public void bind()
	{
		this.combo.valueProperty().bindBidirectional(this.axisOrder);
	}

	@Override
	public void unbind()
	{
		this.combo.valueProperty().unbindBidirectional(this.axisOrder);
	}


	private Node createNode()
	{
		final Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		final HBox hbox       = new HBox(combo, spacer);
		final TitledPane pane = TitledPanes.createCollapsed("Axis Order", hbox);
		return pane;
	}

}
