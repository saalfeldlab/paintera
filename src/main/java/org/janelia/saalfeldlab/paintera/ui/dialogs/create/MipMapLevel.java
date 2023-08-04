package org.janelia.saalfeldlab.paintera.ui.dialogs.create;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.janelia.saalfeldlab.fx.ui.NumberField;
import org.janelia.saalfeldlab.fx.ui.ObjectField;
import org.janelia.saalfeldlab.fx.ui.SpatialField;

public class MipMapLevel {

	final SpatialField<IntegerProperty> relativeDownsamplingFactors;
	final SpatialField<IntegerProperty> absoluteDownsamplingFactors;
	final SpatialField<DoubleProperty> resolution;
	private Boolean showAbsolute = false;

	final NumberField<IntegerProperty> maxNumberOfEntriesPerSet;

	final double fieldWidth;

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
		this.absoluteDownsamplingFactors = SpatialField.intField(1, x -> true, fieldWidth);
		this.absoluteDownsamplingFactors.setEditable(false);
		this.resolution = SpatialField.doubleField(1.0, x -> true, fieldWidth);
		this.resolution.setEditable(false);
		this.maxNumberOfEntriesPerSet = maxNumberOfEntriesPerSet;
		this.maxNumberOfEntriesPerSet.getTextField().setPrefWidth(fieldWidth);
		this.fieldWidth = fieldWidth;


	}

	public Node makeNode() {
		final HBox relativeFactorsHeader = createHeader("Relative Factors");
		final HBox absoluteFactorsHeader = createHeader("Absolute Factors");
		final HBox resolutionHeader = createHeader("Resolution");
		final HBox maxEntriesHeader = createHeader("Max Entries");

		final HBox mipMapRow = new HBox();
		mipMapRow.getChildren().add(new VBox(relativeFactorsHeader, relativeDownsamplingFactors.getNode()));
		if (showAbsolute) {
			mipMapRow.getChildren().add(new VBox(absoluteFactorsHeader, absoluteDownsamplingFactors.getNode()));
			mipMapRow.getChildren().add(new VBox(resolutionHeader, resolution.getNode()));
		}
		mipMapRow.getChildren().add(new VBox(maxEntriesHeader, maxNumberOfEntriesPerSet.getTextField()));

		mipMapRow.setPadding(new Insets(0, 10.0, 0, 10.0));
		mipMapRow.spacingProperty().setValue(10.0);
		return mipMapRow;
	}

	public void displayAbsoluteValues(SpatialField<DoubleProperty> baseResolution, SpatialField<IntegerProperty> absoluteDownsamplingFactors) {

		this.absoluteDownsamplingFactors.getX().valueProperty().unbind();
		this.absoluteDownsamplingFactors.getY().valueProperty().unbind();
		this.absoluteDownsamplingFactors.getZ().valueProperty().unbind();
		this.resolution.getX().valueProperty().unbind();
		this.resolution.getY().valueProperty().unbind();
		this.resolution.getZ().valueProperty().unbind();

		if (baseResolution == null || absoluteDownsamplingFactors == null) {
			showAbsolute = false;
			return;
		}

		final IntegerProperty absXProperty = absoluteDownsamplingFactors.getX().valueProperty();
		final IntegerProperty absYProperty = absoluteDownsamplingFactors.getY().valueProperty();
		final IntegerProperty absZProperty = absoluteDownsamplingFactors.getZ().valueProperty();
		final DoubleBinding xResBinding = baseResolution.getX().valueProperty().multiply(absXProperty);
		final DoubleBinding yResBinding = baseResolution.getY().valueProperty().multiply(absYProperty);
		final DoubleBinding zResBinding = baseResolution.getZ().valueProperty().multiply(absZProperty);

		this.absoluteDownsamplingFactors.getX().valueProperty().bind(absXProperty);
		this.absoluteDownsamplingFactors.getY().valueProperty().bind(absYProperty);
		this.absoluteDownsamplingFactors.getZ().valueProperty().bind(absZProperty);
		this.resolution.getX().valueProperty().bind(xResBinding);
		this.resolution.getY().valueProperty().bind(yResBinding);
		this.resolution.getZ().valueProperty().bind(zResBinding);

		showAbsolute = true;
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
