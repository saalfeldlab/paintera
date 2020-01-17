package org.janelia.saalfeldlab.paintera.state;

import net.imglib2.converter.Converter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;

import java.util.function.LongFunction;

@Deprecated
public interface HasMaskForLabel<T extends IntegerType<T>> {

	LongFunction<Converter<T, BoolType>> maskForLabel();

}
