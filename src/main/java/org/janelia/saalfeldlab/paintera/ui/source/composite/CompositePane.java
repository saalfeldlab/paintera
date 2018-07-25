package org.janelia.saalfeldlab.paintera.ui.source.composite;

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
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaAdd;
import org.janelia.saalfeldlab.paintera.composition.ARGBCompositeAlphaYCbCr;
import org.janelia.saalfeldlab.paintera.composition.Composite;
import org.janelia.saalfeldlab.paintera.composition.CompositeCopy;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

public class CompositePane implements BindUnbindAndNodeSupplier
{

	private static final String ALPHA_ADD = "alpha add";

	private static final String ALPHA_YCBCR = "alpha YCbCr";

	private static final String COPY = "copy";

	private static final ObservableList<String> availableComposites = FXCollections.observableArrayList(
			ALPHA_YCBCR,
			ALPHA_ADD,
			COPY
	                                                                                                   );

	private final ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty;

	private final Composite<ARGBType, ARGBType> composite;

	public CompositePane(final ObjectProperty<Composite<ARGBType, ARGBType>> compositeProperty)
	{
		super();
		this.compositeProperty = compositeProperty;
		this.composite = compositeProperty.get();
	}

	@Override
	public Node get()
	{
		final StringConverter<Composite<ARGBType, ARGBType>> converter = new StringConverter<Composite<ARGBType,
				ARGBType>>()
		{

			@Override
			public String toString(final Composite<ARGBType, ARGBType> object)
			{
				return object instanceof ARGBCompositeAlphaAdd
				       ? ALPHA_ADD
				       : object instanceof CompositeCopy ? COPY : ALPHA_YCBCR;
			}

			@Override
			public Composite<ARGBType, ARGBType> fromString(final String string)
			{
				switch (string)
				{
					case ALPHA_ADD:
						return new ARGBCompositeAlphaAdd();
					case ALPHA_YCBCR:
						return new ARGBCompositeAlphaYCbCr();
					case COPY:
						return new CompositeCopy<>();
					default:
						return null;
				}
			}
		};

		final ComboBox<String> combo = new ComboBox<>(availableComposites);
		combo.setValue(converter.toString(composite));
		combo.valueProperty().addListener((obs, oldv, newv) -> this.compositeProperty.set(converter.fromString(newv)));
		final Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		final HBox hbox       = new HBox(combo, spacer);
		final TitledPane pane = new TitledPane("ARGB Composite", hbox);
		pane.setExpanded(false);
		return pane;
	}

	@Override
	public void bind()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void unbind()
	{
		// TODO Auto-generated method stub

	}

}
