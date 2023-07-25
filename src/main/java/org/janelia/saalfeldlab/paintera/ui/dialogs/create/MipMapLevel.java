package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;

public class MipMapLevel {

	final SpatialField<IntegerProperty> relativeDownsamplingFactors;

	final NumberField<IntegerProperty> maxNumberOfEntriesPerSet;

	final double fieldWidth;

	final Node node;

	final SimpleBooleanProperty showHeader = new SimpleBooleanProperty(false);

	public MipMapLevel(int downsamplingFactor, int maxNumEntries, double fieldWidth, final double nameWidth, ObjectField.SubmitOn... submitOn) {

		this(
				SpatialField.intField(downsamplingFactor, f -> f > 0, fieldWidth, submitOn),
				NumberField.intField(maxNumEntries, n -> true, submitOn),
				fieldWidth,
				nameWidth);
	}

	protected MipMapLevel(
			SpatialField<IntegerProperty> relativeDownsamplingFactors,
			NumberField<IntegerProperty> maxNumberOfEntriesPerSet,
			final double fieldWidth,
			final double nameWidth) {

		this.relativeDownsamplingFactors = relativeDownsamplingFactors;
		this.maxNumberOfEntriesPerSet = maxNumberOfEntriesPerSet;
		this.fieldWidth = fieldWidth;

		final HBox relativeFactorsHeader = createHeader("Relative Factors");
		final HBox maxEntriesHeader = createHeader("Max Entries");

		final HBox mipMapRow = new HBox(
				new VBox(relativeFactorsHeader, relativeDownsamplingFactors.getNode()),
				new VBox(maxEntriesHeader, maxNumberOfEntriesPerSet.getTextField())
		);


		mipMapRow.setPadding(new Insets(0, 10.0, 0, 10.0));
		mipMapRow.spacingProperty().setValue(10.0);
		this.node = mipMapRow;

	}

	private HBox createHeader(String headerText) {
		final Label label = new Label(headerText);
		final HBox header = new HBox(label);
		header.setAlignment(Pos.BOTTOM_CENTER);
		HBox.setHgrow(label, Priority.ALWAYS);
		header.setPadding(new Insets(0, 0, 3.0, 0));
		header.visibleProperty().bind(showHeader);
		header.managedProperty().bind(showHeader);
		return header;
	}

	public double[] downsamplingFactors() {

		return relativeDownsamplingFactors.asDoubleArray();
	}

	public int maxNumEntries() {

		return this.maxNumberOfEntriesPerSet.valueProperty().get();
	}
}
