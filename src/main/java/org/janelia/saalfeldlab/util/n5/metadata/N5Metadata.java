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

  String getPath();
}
