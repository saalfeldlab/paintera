package org.janelia.saalfeldlab.paintera;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.paintera.serialization.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaveProject
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void persistProperties(final String root, final Properties properties, final GsonBuilder builder)
	throws IOException, ProjectUndefined
	{
		if (root == null) { throw new ProjectUndefined(root); }
		LOG.debug("Persisting properties {} into {}", properties, root);
		N5Helpers.n5Writer(root, builder, 64, 64, 64).setAttribute("", Paintera.PAINTERA_KEY, properties);
	}

	public static class ProjectUndefined extends Exception
	{

		/**
		 *
		 */
		private static final long serialVersionUID = 149042035873558791L;

		public ProjectUndefined(final String project)
		{
			super("Project undefined: " + project);
		}

	}

}
