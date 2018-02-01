package bdv.net.imglib2.util;

public class ValueTriple< A, B, C > implements Triple< A, B, C >
{

	private final A a;

	private final B b;

	private final C c;

	public ValueTriple( final A a, final B b, final C c )
	{
		super();
		this.a = a;
		this.b = b;
		this.c = c;
	}

	@Override
	public A getA()
	{
		return a;
	}

	@Override
	public B getB()
	{
		// TODO Auto-generated method stub
		return b;
	}

	@Override
	public C getC()
	{
		// TODO Auto-generated method stub
		return c;
	}

}
