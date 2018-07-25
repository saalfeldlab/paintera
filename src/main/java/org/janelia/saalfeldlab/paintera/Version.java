package org.janelia.saalfeldlab.paintera;

import java.lang.invoke.MethodHandles;

import org.scijava.util.VersionUtils;

public class Version
{

	public static final String VERSION_STRING = VersionUtils.getVersion(MethodHandles.lookup().lookupClass());

}
