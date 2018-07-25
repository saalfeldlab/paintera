package org.janelia.saalfeldlab.paintera.config;

public class Config
{

	public final NavigationConfig navigation;

	public final OrthoSliceConfig orthoSlices;

	public final CrosshairConfig crosshairs;

	public Config(
			final NavigationConfig navigation,
			final OrthoSliceConfig orthoSlices,
			final CrosshairConfig crosshairs)
	{
		super();
		this.navigation = navigation;
		this.orthoSlices = orthoSlices;
		this.crosshairs = crosshairs;
	}

}
