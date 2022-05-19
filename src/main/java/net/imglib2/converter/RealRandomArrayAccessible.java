package net.imglib2.converter;

import net.imglib2.Localizable;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.Type;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class RealRandomArrayAccessible<T extends Type<T>> implements RealRandomAccessible<T> {

  final protected List<RealRandomAccessible<T>> inputs;
  private final BiConsumer<List<T>, T> reducer;
  private final T result;

  public RealRandomArrayAccessible(List<RealRandomAccessible<T>> inputs, BiConsumer<List<T>, T> reducer, T result) {

	this.inputs = inputs;
	this.reducer = reducer;
	this.result = result.copy();
  }

  @Override
  public int numDimensions() {

	return inputs.get(0).numDimensions();
  }

  @Override
  public RealRandomArrayAccess realRandomAccess() {

	return new RealRandomArrayAccess(realRandomAccessList(), this.reducer, this.result);
  }

  private List<RealRandomAccess<T>> realRandomAccessList() {

	return this.inputs.stream().map(RealRandomAccessible::realRandomAccess).collect(Collectors.toList());
  }

  @Override
  public RealRandomArrayAccess realRandomAccess(final RealInterval interval) {

	return new RealRandomArrayAccess(realRandomAccessList(), this.reducer, this.result);
  }

  public class RealRandomArrayAccess implements net.imglib2.RealRandomAccess<T> {

	final protected List<RealRandomAccess<T>> inputs;
	private final BiConsumer<List<T>, T> reducer;
	private final T result;

	public RealRandomArrayAccess(final List<RealRandomAccess<T>> inputs, final BiConsumer<List<T>, T> reducer, final T result) {

	  this.inputs = inputs;
	  this.reducer = reducer;
	  this.result = result.copy();
	}

	@Override
	public void localize(final float[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.localize(position);
	  }
	}

	@Override
	public void localize(final double[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.localize(position);
	  }
	}

	@Override
	public float getFloatPosition(final int d) {

	  return inputs.get(0).getFloatPosition(d);
	}

	@Override
	public double getDoublePosition(final int d) {

	  return inputs.get(0).getDoublePosition(d);
	}

	@Override
	public void fwd(final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.fwd(d);
	  }
	}

	@Override
	public void bck(final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.bck(d);
	  }
	}

	@Override
	public void move(final int distance, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance, d);
	  }
	}

	@Override
	public void move(final long distance, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance, d);
	  }
	}

	@Override
	public void move(final float distance, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance, d);
	  }
	}

	@Override
	public void move(final double distance, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance, d);
	  }
	}

	@Override
	public void move(final Localizable localizable) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(localizable);
	  }
	}

	@Override
	public void move(final int[] distance) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance);
	  }
	}

	@Override
	public void move(final long[] distance) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance);
	  }
	}

	@Override
	public void move(final RealLocalizable distance) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance);
	  }
	}

	@Override
	public void move(final float[] distance) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance);
	  }
	}

	@Override
	public void move(final double[] distance) {

	  for (RealRandomAccess<T> input : inputs) {
		input.move(distance);
	  }
	}

	@Override
	public void setPosition(final Localizable localizable) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(localizable);
	  }
	}

	@Override
	public void setPosition(final int[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position);
	  }
	}

	@Override
	public void setPosition(final long[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position);
	  }
	}

	@Override
	public void setPosition(final float[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position);
	  }
	}

	@Override
	public void setPosition(final double[] position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position);
	  }
	}

	@Override
	public void setPosition(final RealLocalizable position) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position);
	  }
	}

	@Override
	public void setPosition(final int position, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position, d);
	  }
	}

	@Override
	public void setPosition(final long position, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position, d);
	  }
	}

	@Override
	public void setPosition(final float position, final int d) {

	  for (RealRandomAccess<T> input : inputs) {
		input.setPosition(position, d);
	  }
	}

	@Override
	public void setPosition(final double position, final int d) {

	  for (RealRandomAccess<T> input : inputs) {

		final var pos = positionAsRealPoint();
		final var posi = input.positionAsRealPoint();
		input.setPosition(position, d);
	  }
	}

	@Override
	public T get() {

	  final var tArray = inputs.stream().map(RealRandomAccess::get).collect(Collectors.toList());
	  this.reducer.accept(tArray, result);
	  return result;
	}

	@Override
	public RealRandomArrayAccess copy() {

	  final RealRandomArrayAccess copy = new RealRandomArrayAccess(this.inputs, this.reducer, this.result.copy());
	  copy.setPosition(this);
	  return copy;
	}

	@Override
	public int numDimensions() {

	  return inputs.get(0).numDimensions();
	}

	@Override
	public RealRandomAccess<T> copyRealRandomAccess() {

	  return copy();
	}
  }

}
