package org.janelia.saalfeldlab.paintera.serialization.fx;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import javafx.scene.transform.Affine;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.scijava.plugin.Plugin;

import java.lang.reflect.Type;

@Plugin(type = PainteraSerialization.PainteraAdapter.class)
public class AffineSerializer implements PainteraSerialization.PainteraAdapter<Affine> {

  @Override
  public Affine deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

	final double[] v = context.deserialize(json, double[].class);
	return new Affine(
			v[0], v[1], v[2], v[3],
			v[4], v[5], v[6], v[7],
			v[8], v[9], v[10], v[11]);
  }

  @Override
  public JsonElement serialize(Affine a, Type typeOfSrc, JsonSerializationContext context) {

	return context.serialize(new double[]{
			a.getMxx(), a.getMxy(), a.getMxz(), a.getTx(),
			a.getMyx(), a.getMyy(), a.getMyz(), a.getTy(),
			a.getMzx(), a.getMzy(), a.getMzz(), a.getTz()
	});
  }

  @Override
  public Class<Affine> getTargetClass() {

	return Affine.class;
  }

  @Override
  public boolean isHierarchyAdapter() {

	return false;
  }
}
