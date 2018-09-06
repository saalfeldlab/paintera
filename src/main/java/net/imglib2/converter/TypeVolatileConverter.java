package net.imglib2.converter;

import net.imglib2.Volatile;
import net.imglib2.type.Type;

public class TypeVolatileConverter<T extends Type<T>, V extends Volatile<T>> implements Converter<T, V> {

	@Override
	public void convert(T input, V output) {
		output.setValid(true);
		output.get().set(input);
	}

}
