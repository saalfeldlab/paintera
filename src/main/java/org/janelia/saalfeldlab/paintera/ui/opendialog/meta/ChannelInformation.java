package org.janelia.saalfeldlab.paintera.ui.opendialog.meta;

import javafx.beans.property.*;
import javafx.collections.ObservableIntegerArray;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;

public class ChannelInformation {

	private final IntegerProperty numChannels = new SimpleIntegerProperty(1);

	private final IntegerProperty channelsPerSource = new SimpleIntegerProperty(-1);

	private final BooleanProperty revertChannelAxis = new SimpleBooleanProperty(false);

	{
		numChannels.addListener((obs, oldv, newv) -> {
			if (!channelsPerSource.isBound() && channelsPerSource.intValue() < 1)
				channelsPerSource.set(newv.intValue());
		});

	}

	public IntegerProperty numChannelsProperty()
	{
		return numChannels;
	}

	public IntegerProperty channelsPerSourceProperty()
	{
		return channelsPerSource;
	}

	public BooleanProperty revertChannelAxisProperty()
	{
		return revertChannelAxis;
	}

	public void bindTo(ChannelInformation that)
	{
		this.numChannels.bind(that.numChannels);
		this.channelsPerSource.bind(that.channelsPerSource);
		this.revertChannelAxis.bind(that.revertChannelAxis);
	}

	public Node getNode()
	{
		NumberField<IntegerProperty> channelsPerSourceField = NumberField.intField(
				-1,
				nc -> nc > 0 && nc <= numChannels.get(),
				ObjectField.SubmitOn.ENTER_PRESSED, ObjectField.SubmitOn.FOCUS_LOST);
		final CheckBox revertChannelAxisCheckBox = new CheckBox("Revert Channel Order");

		channelsPerSourceField.valueProperty().bindBidirectional(channelsPerSource);
		revertChannelAxisCheckBox.selectedProperty().bindBidirectional(revertChannelAxis);

		Label numChannelsLabel = Labels.withTooltip(
				"Number of channels per source",
				"Will split channel source into multiple sources with at max this many channels.");
		HBox.setHgrow(numChannelsLabel, Priority.ALWAYS);
		channelsPerSourceField.textField().setPrefWidth(100);

		return new VBox(
				new Label("Channel Settings"),
				new HBox(numChannelsLabel, channelsPerSourceField.textField()),
				revertChannelAxisCheckBox);
	}

}
