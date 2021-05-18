package org.janelia.saalfeldlab.util.n5.metadata;

import com.google.gson.GsonBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.paintera.serialization.AffineTransform3DJsonAdapter;

public interface N5Metadata {

  static GsonBuilder getGsonBuilder() {

	final GsonBuilder gsonBuilder = new GsonBuilder();
	registerGsonTypeAdapters(gsonBuilder);
	return gsonBuilder;
  }

  static void registerGsonTypeAdapters(final GsonBuilder gsonBuilder) {

	gsonBuilder.registerTypeAdapter(AffineTransform3D.class, new AffineTransform3DJsonAdapter());
  }

  /**
   * @return the path to this metadata, with respect to the base of the container
   */
  String getPath();

  default String getName() {

	String[] split = getPath().split("/");
	return split[split.length - 1];
  }
}
