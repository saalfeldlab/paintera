package bdv.labels.labelset;

import gnu.trove.list.array.TIntArrayList;

import java.util.List;

public class SuperVoxelListsExample
{
	public static void main( final String[] args )
	{
		final LabelMultisetEntry svo = new LabelMultisetEntry();
		System.out.println( svo );
		svo.setId( 1L );
		svo.setCount( 5 );
		System.out.println( svo );
		System.out.println();

		final LongMappedAccessData data = LongMappedAccessData.factory.createStorage( 1024 );

		final RefList< LabelMultisetEntry > list = new MappedObjectArrayList<>( LabelMultisetEntry.type, data, 0 );
		list.add( new LabelMultisetEntry( 2, 10 ) );
		list.add( svo );
		svo.setId( 3L );
		svo.setCount( 15 );
		list.add( svo );
		System.out.println( list );
		System.out.println( svo );
		final LabelMultisetEntry ref = list.createRef();
		System.out.println( list.get( 1, ref ) );
		list.releaseRef( ref );
		System.out.println();

		for ( int i = 0; i < 20; ++i )
			list.add( new LabelMultisetEntry( i, i+1 ) );
		System.out.println( list );
		System.out.println();
		System.out.println();

		final TIntArrayList lists = new TIntArrayList();
		lists.add( 0 );
		for ( int l = 0; l < 10; ++l )
		{
			final long oldOffset = lists.get( lists.size() - 1 );
			final long oldSizeInBytes = new MappedObjectArrayList<>( LabelMultisetEntry.type, data, oldOffset ).getSizeInBytes();
			final long baseOffset = oldOffset + oldSizeInBytes;
			final List< LabelMultisetEntry > ll = new MappedObjectArrayList<>( LabelMultisetEntry.type, data, baseOffset );
			for ( int i = 0; i < 5; ++i )
				ll.add( new LabelMultisetEntry( i, 10 ) );
			lists.add( ( int ) baseOffset );
			for ( int li = 0; li < lists.size(); ++li )
				System.out.println( new MappedObjectArrayList<>( LabelMultisetEntry.type, data, lists.get( li ) ) );
			System.out.println();
		}
		System.out.println();

		final List< LabelMultisetEntry > l3 = new MappedObjectArrayList<>( LabelMultisetEntry.type, data, lists.get( 3 ) );
		final List< LabelMultisetEntry > l5 = new MappedObjectArrayList<>( LabelMultisetEntry.type, data, lists.get( 5 ) );
		System.out.println( list.equals( l3 ) );
		System.out.println( l5.equals( l3 ) );
	}
}
