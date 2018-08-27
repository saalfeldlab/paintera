package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.Volatile;
import net.imglib2.type.Type;

public class VolatileWithSet<T extends Type<T>> extends Volatile<T> implements Type<VolatileWithSet<T>> {


	private T t;

	public VolatileWithSet(T t, boolean valid) {
		super(t, valid);
		this.t = t;
	}

	@Override
	public T get()
	{
		return getT();
	}

	public T getT() {
		return t;
	}

	public void setT(T t) {
		this.t = t;
	}

	@Override
	public VolatileWithSet<T> createVariable() {
		return new VolatileWithSet<>(t == null ? t : t.createVariable(), true);
	}

	@Override
	public VolatileWithSet<T> copy() {
		return new VolatileWithSet<>(t == null ? t : t.copy(), isValid());
	}

	@Override
	public void set(VolatileWithSet<T> c) {

	}

	@Override
	public boolean valueEquals(VolatileWithSet<T> that) {
		return that.t.valueEquals(this.t);
	}
}
