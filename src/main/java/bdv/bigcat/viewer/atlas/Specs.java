package bdv.bigcat.viewer.atlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import bdv.bigcat.viewer.atlas.converter.ARGBIdentiyWithAlpha;
import bdv.bigcat.viewer.atlas.data.DatasetSpec;
import bdv.bigcat.viewer.state.AbstractState;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.display.LinearRange;
import net.imglib2.type.numeric.ARGBType;

public class Specs extends AbstractState< Specs >
{

	private final ObservableList< SourceState< ?, ?, ? > > sourceStates = FXCollections.observableArrayList();

	private final Property< Optional< DatasetSpec< ?, ? > > > selectedSource = new SimpleObjectProperty<>( Optional.empty() );

	private final ArrayList< Runnable > visibiltyChangeListeners = new ArrayList<>();
//	{
//		this.selectedSource.addListener( ( ChangeListener< Optional< DatasetSpec< ?, ? > > > ) ( obs, old, newv ) -> {
//			System.out.println( "Got change " + obs + " " + old + " " + newv + " " + sourceStates );
//		} );
//	}

	public static class SourceState< T, U, V >
	{

		private final DatasetSpec< T, U > spec;

		private final Source< V > source;

		private final Converter< V, ARGBType > converter;

		private final SimpleBooleanProperty isVisible = new SimpleBooleanProperty( true );

		private final SimpleIntegerProperty alpha;

		private final SimpleDoubleProperty min;

		private final SimpleDoubleProperty max;

		public SourceState( final DatasetSpec< T, U > spec, final Source< V > source, final Converter< V, ARGBType > converter, final int alpha, final double min, final double max )
		{
			super();
			this.spec = spec;
			this.source = source;
			this.converter = converter;
			this.alpha = new SimpleIntegerProperty( alpha );
			this.min = new SimpleDoubleProperty( min );
			this.max = new SimpleDoubleProperty( max );
		}

		public DatasetSpec< T, U > spec()
		{
			return spec;
		}

		public Source< V > source()
		{
			return source;
		}

		public Converter< V, ARGBType > converter()
		{
			return converter;
		}

		public boolean isVisible()
		{
			return isVisible.get();
		}

		public SourceAndConverter< V > sourceAndConverter()
		{
			return new SourceAndConverter<>( source, converter );
		}

		@Override
		public int hashCode()
		{
			return spec.hashCode();
		}

		@Override
		public boolean equals( final Object o )
		{
			return spec.equals( o );
		}

		public void setAlpha( final int alpha )
		{
			this.alpha.set( alpha );
		}

		public void setMin( final double min )
		{
			this.min.set( min );
		}

		public void setMax( final double max )
		{
			this.max.set( max );
		}

		public void setMinMax( final double min, final double max )
		{
			setMin( min );
			setMax( max );
		}

		public IntegerProperty alphaProperty()
		{
			return alpha;
		}

		public DoubleProperty minProperty()
		{
			return min;
		}

		public DoubleProperty maxProperty()
		{
			return maxProperty();
		}

		public BooleanProperty visibleProperty()
		{
			return isVisible;
		}

	}

	public void addListChangeListener( final ListChangeListener< SourceState< ?, ?, ? > > listener )
	{
		this.sourceStates.addListener( listener );
	}

	private List< SourceAndConverter< ? > > getSourceAndConverters( final Predicate< SourceState< ?, ?, ? > > filter )
	{
		synchronized ( sourceStates )
		{
			return sourceStates.stream().filter( filter::test ).map( SourceState::sourceAndConverter ).collect( Collectors.toList() );
		}
	}

	public List< SourceAndConverter< ? > > getSourceAndConverters( final DatasetSpec< ?, ? > spec )
	{
		return getSourceAndConverters( s -> s.spec.equals( spec ) );
	}

	public List< SourceAndConverter< ? > > getSourceAndConverters()
	{
		return getSourceAndConverters( state -> true );
	}

//	public

	public < T, U, V > void addSpec( final DatasetSpec< T, U > spec, final Source< V > source, final Converter< V, ARGBType > converter )
	{
		synchronized ( sourceStates )
		{
			final int alpha;
			if ( converter instanceof RealARGBConverter< ? > )
				alpha = ( ( RealARGBConverter< ? > ) converter ).getAlpha();
			else if ( converter instanceof ARGBIdentiyWithAlpha )
				alpha = ( ( ARGBIdentiyWithAlpha ) converter ).getAlpha();
			else
				alpha = 255;

			final double min;
			final double max;
			if ( converter instanceof LinearRange )
			{
				min = ( ( LinearRange ) converter ).getMin();
				max = ( ( LinearRange ) converter ).getMax();
			}
			else
			{
				min = 0.0;
				max = 255.0;
			}

			sourceStates.add( new SourceState<>( spec, source, converter, alpha, min, max ) );

			synchronized ( selectedSource )
			{
				if ( !selectedSource.getValue().isPresent() )
					selectedSource.setValue( Optional.of( spec ) );
			}

//			stateChanged();
		}
	}

	public < T > void removeSpec( final DatasetSpec< ?, T > spec )
	{
		synchronized ( sourceStates )
		{
			final List< SourceState< ?, ?, ? > > matches = sourceStates.stream().filter( state -> state.spec.equals( spec ) ).collect( Collectors.toList() );
			if ( matches.size() > 0 )
				synchronized ( selectedSource )
				{
					final Optional< DatasetSpec< ?, ? > > newSelection;
					if ( selectedSource.getValue().isPresent() )
					{
						final int selectionIndex = Math.min( indexOf( sourceStates, spec ) + 1, sourceStates.size() - 1 - matches.size() );
						if ( selectionIndex < 0 )
							newSelection = Optional.empty();
						else
							newSelection = Optional.of( sourceStates.get( selectionIndex ).spec() );
					}
					else
						newSelection = Optional.empty();
					matches.forEach( sourceStates::remove );
					selectedSource.setValue( newSelection );
					stateChanged();
				}
		}
	}

	public void setVisibility( final DatasetSpec< ?, ? > spec, final boolean isVisible )
	{
		synchronized ( sourceStates )
		{
			final List< SourceState< ?, ?, ? > > matches = sourceStates.stream().filter( state -> state.spec.equals( spec ) ).collect( Collectors.toList() );
			if ( matches.stream().filter( match -> match.isVisible.get() != isVisible ).count() > 0 )
			{
				matches.forEach( match -> match.isVisible.set( isVisible ) );
				this.visibiltyChangeListeners.forEach( Runnable::run );
			}
		}
	}

	public void setAlpha( final DatasetSpec< ?, ? > spec, final int alpha )
	{
		synchronized ( sourceStates )
		{
			sourceStates.stream().filter( state -> state.spec.equals( spec ) ).forEach( state -> state.setAlpha( alpha ) );
		}
	}

	public void setMin( final DatasetSpec< ?, ? > spec, final double min )
	{
		synchronized ( sourceStates )
		{
			sourceStates.stream().filter( state -> state.spec.equals( spec ) ).forEach( state -> state.setMin( min ) );
		}
	}

	public void setMax( final DatasetSpec< ?, ? > spec, final double max )
	{
		synchronized ( sourceStates )
		{
			sourceStates.stream().filter( state -> state.spec.equals( spec ) ).forEach( state -> state.setMax( max ) );
		}
	}

	public void setMinMax( final DatasetSpec< ?, ? > spec, final double min, final double max )
	{
		synchronized ( sourceStates )
		{
			sourceStates.stream().filter( state -> state.spec.equals( spec ) ).forEach( state -> state.setMinMax( min, max ) );
		}
	}

	public List< SourceState< ?, ?, ? > > sourceStates()
	{
		synchronized ( sourceStates )
		{
			return new ArrayList<>( sourceStates );
		}
	}

	public Property< Optional< DatasetSpec< ?, ? > > > selectedSourceProperty()
	{
		return selectedSource;
	}

	public Optional< SourceState< ?, ?, ? > > getState( final Source< ? > source )
	{
		synchronized ( sourceStates )
		{
			return sourceStates.stream().filter( state -> state.source == source ).findFirst();
		}
	}

	public static int indexOf( final Collection< SourceState< ?, ?, ? > > states, final DatasetSpec< ?, ? > spec )
	{
		final Iterator< SourceState< ?, ?, ? > > iterator = states.iterator();
		for ( int i = 0; iterator.hasNext(); ++i )
			if ( iterator.next().equals( spec ) )
				return i;
		return -1;
	}

	public void addVisibilityChangedListener( final Runnable listener )
	{
		this.visibiltyChangeListeners.add( listener );
	}

}
