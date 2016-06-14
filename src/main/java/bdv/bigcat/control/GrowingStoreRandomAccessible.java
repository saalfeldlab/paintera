package bdv.bigcat.control;

import ij.ImageJ;
import net.imglib2.*;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Created by hanslovskyp on 6/13/16.
 */
class GrowingStoreRandomAccessible< T extends Type< T > > implements RandomAccessible< T >
{

    public interface Factory< U extends Type< U > >
    {
        RandomAccessibleInterval< U > createStore( long[] min, long[] max, U u );

        default RandomAccessibleInterval< U > createStore( long[] dimensions, U u ) {
            long[] min = new long[ dimensions.length ];
            long[] max = new long[ dimensions.length ];
            for ( int d = 0; d < min.length; ++d )
            {
                min[d] = 0;
                max[d] = dimensions[d] - 1;
            }
            return createStore( min, max, u );
        }
    }

    private final int nDim;
    private final long[] min;
    private final long[] max;
    private final long[] dimensions;
    private RandomAccessibleInterval< T > store;
    private final Factory< T > factory;
    private final double resizeFactor;
    private final double resizeFactorMinusOne;
    T t;
    private final ArrayList< WeakReference< GrowingStoreRandomAccess > > randomAccessRefs;

    public enum SIDE { TOP, BOTTOM }


    public GrowingStoreRandomAccessible(
            long[] initialDimension,
            final Factory< T > factory,
            final double resizeFactor,
            final T t
    )
    {
        this( factory.createStore( initialDimension, t ), factory, resizeFactor );
    }


    public GrowingStoreRandomAccessible(
            long[] initialMin,
            long[] initialMax,
            Factory< T > factory,
            final double resizeFactor,
            T t
    )
    {
        this( factory.createStore( initialMin, initialMax, t ), factory, resizeFactor );
    }


    public GrowingStoreRandomAccessible(
            RandomAccessibleInterval< T > initialStore,
            Factory< T > factory,
            final double resizeFactor
    )
    {
        this.nDim = initialStore.numDimensions();
        this.min = new long[ this.nDim ];
        this.max = new long[ this.nDim ];
        this.dimensions = new long[ this.nDim ];
        updateStore( initialStore );
        this.factory = factory;
        this.resizeFactor = resizeFactor;
        this.resizeFactorMinusOne = resizeFactor - 1;
        this.t = initialStore.randomAccess().get().createVariable();
        this.randomAccessRefs = new ArrayList<>();
    }

    private void growStore( int d, SIDE side, long minDiff )
    {
        long diff = Math.max( minDiff, (long) ( this.dimensions[d] * resizeFactorMinusOne ) );
        switch ( side )
        {
            case TOP:
                growStoreTop( d, diff );
                break;
            case BOTTOM:
                growStoreBottom( d, diff );
                break;
        }
    }

    private void growStoreTop( int d, long diff )
    {
        this.dimensions[ d ] += diff;
        this.max[ d ] += diff;

        RandomAccessibleInterval<T> newStore = this.factory.createStore( this.min, this.max, t );
        Cursor<T> cursor = Views.iterable( this.store ).cursor();
        RandomAccess<T> access = newStore.randomAccess();
        while( cursor.hasNext() )
        {
            T val = cursor.next();
            access.setPosition( cursor );
            access.get().set( val );
        }
        this.store = newStore;
    }

    private void growStoreBottom( int d, long diff )
    {
        long[] oldDimensions = this.dimensions.clone();
        final long[] dimensionsDiff = new long[ this.dimensions.length ];
        dimensionsDiff[ d ] = diff;
        this.dimensions[ d ] += dimensionsDiff[ d ];
        this.min[ d ] -= dimensionsDiff[ d ];

        RandomAccessibleInterval<T> newStore = this.factory.createStore( this.min, this.max, t );
        Cursor<T> cursor = Views.iterable( this.store ).cursor();
        RandomAccess<T> access = newStore.randomAccess();
        while( cursor.hasNext() )
        {
            T val = cursor.next();
            access.setPosition( cursor );
            access.get().set( val );
        }
        this.store = newStore;
    }

    private void updateStore( RandomAccessibleInterval< T > newStore )
    {
        newStore.dimensions( this.dimensions );
        newStore.min( this.min );
        newStore.max( this.max );
        this.store = newStore;
    }

//    @Override
//    public long min(int d) {
//        return this.min[d];
//    }
//
//    @Override
//    public void min(long[] min) {
//        System.arraycopy( this.min, 0, min, 0, this.min.length );
//    }
//
//    @Override
//    public void min(Positionable min) {
//        min.setPosition( this.min );
//    }
//
//    @Override
//    public long max(int d) {
//        return this.max[d];
//    }
//
//    @Override
//    public void max(long[] max) {
//        System.arraycopy( this.max, 0, max, 0, this.max.length );
//    }
//
//    @Override
//    public void max(Positionable max) {
//        max.setPosition( this.max );
//    }
//
//    @Override
//    public void dimensions(long[] dims) {
//        System.arraycopy( this.dimensions, 0, dims, 0, this.dimensions.length );
//    }
//
//    @Override
//    public long dimension(int d) {
//        return this.dimensions[d];
//    }

    @Override
    public RandomAccess<T> randomAccess() {

        synchronized ( randomAccessRefs ) {
            this.cleanRandomAccessRefs();
            GrowingStoreRandomAccess access = new GrowingStoreRandomAccess();
            this.randomAccessRefs.add( new WeakReference<GrowingStoreRandomAccess>( access ) );
            return access;

        }
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        return Views.interval( this.store, interval ).randomAccess();
    }

    public IntervalView< T > getIntervalOfSizeOfStore() {
        synchronized ( this.store )
        {
            return Views.interval( this.store, new FinalInterval( this.min, this.max ) );
        }
    }

    private void cleanRandomAccessRefs() {
        synchronized ( this.randomAccessRefs )
        {
            for ( int i = this.randomAccessRefs.size() - 1; i >= 0; --i )
            {
                if ( this.randomAccessRefs.get( i ).get() == null )
                    this.randomAccessRefs.remove( i );
            }
        }
    }

    private void updateRandomAccessRefs() {
        synchronized ( this.randomAccessRefs )
        {
            for ( int i = this.randomAccessRefs.size() - 1; i >= 0; --i )
            {
                GrowingStoreRandomAccess ra = this.randomAccessRefs.get(i).get();
                if ( ra == null )
                    this.randomAccessRefs.remove( i );
                else {
                    ra.storeRandomAccess = store.randomAccess();
                }
            }
        }
    }

//    @Override
//    public double realMin(int d) {
//        return min(d);
//    }
//
//    @Override
//    public void realMin(double[] min) {
//        for ( int d = 0; d < this.min.length; ++d )
//            min[d] = this.min[d];
//    }
//
//    @Override
//    public void realMin(RealPositionable min) {
//        min.setPosition( this.min );
//    }
//
//    @Override
//    public double realMax(int d) {
//        return max(d);
//    }
//
//    @Override
//    public void realMax(double[] max) {
//        for ( int d = 0; d < this.max.length; ++d )
//            max[d] = this.max[d];
//    }
//
//    @Override
//    public void realMax(RealPositionable max) {
//        max.setPosition( this.max );
//    }

    @Override
    public int numDimensions() {
        return this.nDim;
    }


    public class GrowingStoreRandomAccess extends Point implements RandomAccess< T >
    {

        private RandomAccess< T > storeRandomAccess;

        private GrowingStoreRandomAccess()
        {
            this( store.randomAccess() );
        }

        private GrowingStoreRandomAccess(RandomAccess< T > storeRandomAccess )
        {
            super( nDim );
            this.storeRandomAccess = storeRandomAccess;
        }



        @Override
        public RandomAccess<T> copyRandomAccess() {
            return new GrowingStoreRandomAccess( this.storeRandomAccess.copyRandomAccess() );
        }

        @Override
        public T get() {
            boolean changedStore = false;
            synchronized ( GrowingStoreRandomAccess.this ) {
                for (int d = 0; d < nDim; ++d) {
                    long pos = this.position[d];
                    while (pos < min[d]) {
                        growStore(d, SIDE.BOTTOM, 1);
                        changedStore = true;
                    }
                    while (pos > max[d]) {
                        System.out.println(pos + " " + max[d] + " " + d);
                        growStore(d, SIDE.TOP, 1);
                        changedStore = true;
                    }
                }
            }
            if (changedStore) {
                this.storeRandomAccess = store.randomAccess();
            }

            this.storeRandomAccess.setPosition(this.position);

            return this.storeRandomAccess.get();

        }

        @Override
        public Sampler<T> copy() {
            return copyRandomAccess();
        }

//        @Override
//        public void setPosition( long[] pos )
//        {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( pos[ d ], d );
//        }
//
//        @Override
//        public void fwd(int d) {
////            long newPos = this.storeRandomAccess.getLongPosition(d) + 1;
////            if ( newPos > max[ d ] ) {
////                growStore( d, SIDE.TOP, 1 );
////                RandomAccess<T> oldRandomAccess = this.storeRandomAccess;
////                this.storeRandomAccess = store.randomAccess();
////                this.storeRandomAccess.setPosition( oldRandomAccess );
////            }
//            this.storeRandomAccess.fwd( d );
//        }
//
//        @Override
//        public void bck(int d) {
////            long newPos = this.storeRandomAccess.getLongPosition(d) - 1;
////            if ( newPos < min[ d ] ) {
////                growStore( d, SIDE.BOTTOM, 1 );
////                RandomAccess<T> oldRandomAccess = this.storeRandomAccess;
////                this.storeRandomAccess = store.randomAccess();
////                this.storeRandomAccess.setPosition( oldRandomAccess );
////            }
//            this.storeRandomAccess.bck( d );
//        }
//
//        @Override
//        public void move(int pos, int d) {
//            setPosition( storeRandomAccess.getLongPosition( d ) + pos, d );
//        }
//
//        @Override
//        public void move(long pos, int d) {
//            setPosition( storeRandomAccess.getLongPosition( d ) + pos, d );
//        }
//
//        @Override
//        public void move(Localizable step) {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( storeRandomAccess.getLongPosition( d ) + step.getLongPosition( d ), d );
//        }
//
//        @Override
//        public void move(int[] step) {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( storeRandomAccess.getLongPosition( d ) + step[ d ], d );
//        }
//
//        @Override
//        public void move(long[] step) {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( storeRandomAccess.getLongPosition( d ) + step[ d ], d );
//        }
//
//        @Override
//        public void setPosition( Localizable pos )
//        {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( pos.getLongPosition( d ), d );
//        }
//
//        @Override
//        public void setPosition(int[] pos) {
//            for ( int d = 0; d < nDim; ++d )
//                setPosition( pos[ d ], d );
//        }
//
//        @Override
//        public void setPosition( long pos, int d )
//        {
//            if ( pos < min[ d ] )
//            {
//                long minDiff = min[ d ] - pos;
//                growStore( d, SIDE.BOTTOM, minDiff );
//                RandomAccess<T> oldRandomAccess = this.storeRandomAccess;
//                this.storeRandomAccess = store.randomAccess();
//                this.storeRandomAccess.setPosition( oldRandomAccess );
//            }
//            else if ( pos > max[ d ] )
//            {
//                long minDiff = max[ d ] - pos;
//                growStore( d, SIDE.TOP, minDiff );
//                RandomAccess<T> oldRandomAccess = this.storeRandomAccess;
//                this.storeRandomAccess = store.randomAccess();
//                this.storeRandomAccess.setPosition( oldRandomAccess );
//            }
//            this.storeRandomAccess.setPosition( pos, d );
//        }
//
//        @Override
//        public void setPosition( int pos, int d )
//        {
//            setPosition( (long) pos, d );
//        }
//
//        @Override
//        public T get() {
//            for ( int d = 0; d < nDim; ++d )
//            {
//                RandomAccess<T> oldRandomAccess = this.storeRandomAccess;
//                long pos = this.storeRandomAccess.getLongPosition(d);
//                if ( pos < min[d] )
//                {
//                    long minDiff = min[ d ] - pos;
//                    growStore( d, SIDE.BOTTOM, minDiff );
//                    this.storeRandomAccess = store.randomAccess();
//                    this.storeRandomAccess.setPosition( oldRandomAccess );
//                }
//                else if ( pos > max[ d ] )
//                {
//                    long minDiff = max[ d ] - pos;
//                    growStore( d, SIDE.TOP, minDiff );
//                    this.storeRandomAccess = store.randomAccess();
//                    this.storeRandomAccess.setPosition( oldRandomAccess );
//                }
//            }
//
//            return this.storeRandomAccess.get();
//        }
//
//        @Override
//        public Sampler<T> copy() {
//            return copyRandomAccess();
//        }
//
//        @Override
//        public RandomAccess<T> copyRandomAccess() {
//            return new GrowingStoreRandomAccess( storeRandomAccess.copyRandomAccess() );
//        }
//
//        @Override
//        public void localize(int[] loc) {
//            this.storeRandomAccess.localize( loc );
//        }
//
//        @Override
//        public void localize(long[] loc) {
//            this.storeRandomAccess.localize( loc );
//        }
//
//        @Override
//        public int getIntPosition(int d) {
//            return this.storeRandomAccess.getIntPosition( d );
//        }
//
//        @Override
//        public long getLongPosition(int d) {
//            return this.storeRandomAccess.getLongPosition( d );
//        }
//
//        @Override
//        public void localize(float[] loc) {
//            this.storeRandomAccess.localize( loc );
//        }
//
//        @Override
//        public void localize(double[] loc) {
//            this.storeRandomAccess.localize( loc );
//        }
//
//        @Override
//        public float getFloatPosition(int d) {
//            return this.storeRandomAccess.getFloatPosition( d );
//        }
//
//        @Override
//        public double getDoublePosition(int d) {
//            return this.storeRandomAccess.getDoublePosition( d );
//        }
//
//        @Override
//        public int numDimensions() {
//            return nDim;
//        }

        // only for test purposes
        @Override
        public String toString() {
            long[] pos = new long[ nDim ];
            localize( pos );
            return Arrays.toString( pos );
        }
    }


    public static void main(String[] args) {
        final long[] dim = new long[] { 2, 3 };
        long[] m = new long[dim.length];
        long[] M = new long[dim.length];
        ArrayImg<FloatType, FloatArray> img = ArrayImgs.floats(dim);
        int i = 0;
        for ( FloatType c : img )
            c.set( i++ );

        Factory< FloatType > factory = (min1, max1, t1) -> {
            final long[] dimensions1 = new long[ min1.length ];
            for (int d = 0; d < min1.length; ++d )
                dimensions1[d] = max1[d] - min1[d] + 1;
            ArrayImg<FloatType, FloatArray> imgFac = ArrayImgs.floats(dimensions1);
            return Views.translate(imgFac, min1);
        };

        Function< long[], long[] > l = (long[] array) -> {
            long[] result = array.clone();
            for( int k = 0; k < result.length; ++k )
                result[ k ] = -result[ k ];
            return result;
        };

        GrowingStoreRandomAccessible<FloatType> rra =
                new GrowingStoreRandomAccessible<FloatType>(img, factory, 2.0);

        new ImageJ();
        RandomAccess<FloatType> ra = rra.randomAccess();
//        Bdv bdv = BdvFunctions.show(rra, "1");

        ImageJFunctions.show( rra.getIntervalOfSizeOfStore(), "1" );
        System.out.println(Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra );
        ra.setPosition( -2, 0 );
        ra.get().get();
        ImageJFunctions.show( rra.getIntervalOfSizeOfStore(), "2" );
        System.out.println(Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra );
        ra.setPosition( dim[1], 1 );
        ra.get().get();
        ImageJFunctions.show( rra.getIntervalOfSizeOfStore(), "3" );
        System.out.println(Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra );
        ra.setPosition( M );
        ra.setPosition( 32, 0 );
        ra.move( 17, 1 );
        ra.get().get();
        ImageJFunctions.show( rra.getIntervalOfSizeOfStore(), "4" );
        System.out.println(Arrays.toString( dim ) + " " + Arrays.toString( m ) + " " + Arrays.toString( M ) + " " + ra.get().get() + " " + ra );

    }


}
