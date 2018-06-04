package org.janelia.saalfeldlab.paintera.config;

import org.janelia.saalfeldlab.paintera.viewer3d.OrthoSliceFX;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;

public class OrthoSliceConfig
{

	private final BooleanProperty enable = new SimpleBooleanProperty( true );

	private final BooleanProperty showTopLeft = new SimpleBooleanProperty( true );

	private final BooleanProperty showTopRight = new SimpleBooleanProperty( true );

	private final BooleanProperty showBottomLeft = new SimpleBooleanProperty( true );

	final ObservableBooleanValue isTopLeftVisible;

	final ObservableBooleanValue isTopRightVisible;

	final ObservableBooleanValue isBottomLeftVisible;

	private final ObservableBooleanValue hasSources;

	public OrthoSliceConfig(
			final ObservableBooleanValue isTopLeftVisible,
			final ObservableBooleanValue isTopRightVisible,
			final ObservableBooleanValue isBottomLeftVisible,
			final ObservableBooleanValue hasSources )
	{
		super();
		this.isTopLeftVisible = isTopLeftVisible;
		this.isTopRightVisible = isTopRightVisible;
		this.isBottomLeftVisible = isBottomLeftVisible;
		this.hasSources = hasSources;
	}

	public BooleanProperty enableProperty()
	{
		return this.enable;
	}

	public BooleanProperty showTopLeftProperty()
	{
		return this.showTopLeft;
	}

	public BooleanProperty showTopRightProperty()
	{
		return this.showTopRight;
	}

	public BooleanProperty showBottomLeftProperty()
	{
		return this.showBottomLeft;
	}

	public void bindOrthoSlicesToConifg(
			final OrthoSliceFX topLeft,
			final OrthoSliceFX topRight,
			final OrthoSliceFX bottomLeft )
	{
		topLeft.isVisibleProperty().bind( showTopLeft.and( enable ).and( hasSources ).and( isTopLeftVisible ) );
		topRight.isVisibleProperty().bind( showTopRight.and( enable ).and( hasSources ).and( isTopRightVisible ) );
		bottomLeft.isVisibleProperty().bind( showBottomLeft.and( enable ).and( hasSources ).and( isBottomLeftVisible ) );
	}

}
