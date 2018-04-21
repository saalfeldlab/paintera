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

	private final OrthoSliceFX topLeft;

	private final OrthoSliceFX topRight;

	private final OrthoSliceFX bottomLeft;

	public OrthoSliceConfig(
			final OrthoSliceFX topLeft,
			final OrthoSliceFX topRight,
			final OrthoSliceFX bottomLeft,
			final ObservableBooleanValue isTopLeftVisible,
			final ObservableBooleanValue isTopRightVisible,
			final ObservableBooleanValue isBottomLeftVisible,
			final ObservableBooleanValue hasSources )
	{
		super();
		this.topLeft = topLeft;
		this.topRight = topRight;
		this.bottomLeft = bottomLeft;

		this.topLeft.isVisibleProperty().bind( showTopLeft.and( enable ).and( hasSources ).and( isTopLeftVisible ) );
		this.topRight.isVisibleProperty().bind( showTopRight.and( enable ).and( hasSources ).and( isTopRightVisible ) );
		this.bottomLeft.isVisibleProperty().bind( showBottomLeft.and( enable ).and( hasSources ).and( isBottomLeftVisible ) );
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

}
