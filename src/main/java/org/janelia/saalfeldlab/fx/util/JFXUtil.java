package org.janelia.saalfeldlab.fx.util;

import com.sun.javafx.application.PlatformImpl;

public class JFXUtil
{

	public static void platformImplStartup()
	{
		platformImplStartup(() -> {
		});
	}

	public static void platformImplStartup(final Runnable r)
	{
		PlatformImpl.startup(r);
	}

}
