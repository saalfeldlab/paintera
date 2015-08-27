package bdv.util;

public class Pairs
{
	public interface PairInterface<A, B>
	{
		A getFirst();
		B getSecond();
	}
	
	public static class Pair<T, U> implements PairInterface<T, U>
		{
		
		private final T first;
		
		private final U second;
	
		public Pair( T first, U second )
		{
			super();
			this.first = first;
			this.second = second;
		}
		
		public T getFirst()
		{
			return first;
		}
		
		public U getSecond()
		{
			return second;
		}
		
	}
	
	public static class InvertedGetView<T, U> implements PairInterface<T, U>
	{
		
		private final PairInterface<U, T> pair;
		
		public InvertedGetView( PairInterface< U, T > pair )
		{
			super();
			this.pair = pair;
		}

		public T getFirst()
		{
			return pair.getSecond();
		}
		
		public U getSecond()
		{
			return pair.getFirst();
		}
		
	}
	
	public static <T, U> Pair<T, U> from( T t, U u )
	{
		return new Pair<T, U>(t, u);
	}
	
	public static <T, U> InvertedGetView<T, U> invert( PairInterface<U, T> pair )
	{
		return new InvertedGetView<T, U>( pair );
	}
	
	
	
}
