package bdv.labels.labelset;

public class LabelMultisetEntryListExamples
{
	public static void main( final String[] args )
	{
		final LabelMultisetEntryList list = new LabelMultisetEntryList( 10 );

		list.add( new LabelMultisetEntry( 17, 1 ) );
		list.add( new LabelMultisetEntry( 1, 10 ) );
		list.add( new LabelMultisetEntry( 17, 3 ) );
		list.add( new LabelMultisetEntry( 12, 2 ) );
		list.add( new LabelMultisetEntry( 1, 4 ) );
		list.add( new LabelMultisetEntry( 67, 1 ) );
		list.add( new LabelMultisetEntry( 67, 3 ) );
		list.add( new LabelMultisetEntry( 17, 3 ) );
		System.out.println( list );
		System.out.println();
		System.out.println();

		list.sortById();
		System.out.println( list );
		System.out.println();
		System.out.println();

		list.mergeConsecutiveEntries();
		System.out.println( list );
		System.out.println();
		System.out.println();


		final LabelMultisetEntryList list2 = new LabelMultisetEntryList( 10 );
		list2.add( new LabelMultisetEntry( 0, 1 ) );
		list2.add( new LabelMultisetEntry( 1, 1 ) );
		list2.add( new LabelMultisetEntry( 2, 1 ) );
		list2.add( new LabelMultisetEntry( 16, 1 ) );
		list2.add( new LabelMultisetEntry( 60, 1 ) );
		System.out.println( list2 );
		System.out.println();
		System.out.println();

		list.mergeWith( list2 );
		System.out.println( list );
		System.out.println();
		System.out.println();

		final LabelMultisetEntryList list3 = new LabelMultisetEntryList( 10 );
		System.out.println( list3 );
		list3.mergeWith( list2 );
		System.out.println( list3 );
		list3.mergeWith( list );
		System.out.println( list3 );
		System.out.println();
		System.out.println();
	}
}
