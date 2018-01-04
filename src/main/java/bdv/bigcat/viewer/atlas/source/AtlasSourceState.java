package bdv.bigcat.viewer.atlas.source;

import java.util.function.Function;

import bdv.bigcat.composite.Composite;
import bdv.bigcat.ui.ARGBStream;
import bdv.bigcat.viewer.ToIdConverter;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.mode.Mode;
import bdv.bigcat.viewer.state.FragmentSegmentAssignmentState;
import bdv.bigcat.viewer.state.SelectedIds;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;
import net.imglib2.converter.Converter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public abstract class AtlasSourceState< T, D >
{

	public enum TYPE
	{
		RAW, LABEL
	};

	private final ObjectProperty< Converter< T, ARGBType > > converter = new SimpleObjectProperty<>();

	private final ObjectProperty< Composite< ARGBType, ARGBType > > composite = new SimpleObjectProperty<>();

	private final BooleanProperty visible = new SimpleBooleanProperty();

	private final ObjectProperty< Function< D, Converter< D, BoolType > > > maskGenerator = new SimpleObjectProperty<>();

	private final ObjectProperty< DataSource< D, T > > dataSource = new SimpleObjectProperty<>();

	private final ObjectProperty< TYPE > type = new SimpleObjectProperty<>();

	protected void setConverter( final Converter< T, ARGBType > converter )
	{
		this.converter.set( converter );
	}

	public Converter< T, ARGBType > getConverter()
	{
		return converter.get();
	}

	public ReadOnlyObjectProperty< Converter< T, ARGBType > > converterProperty()
	{
		return this.converter;
	}

	public ObjectProperty< Composite< ARGBType, ARGBType > > compositeProperty()
	{
		return this.composite;
	}

	public BooleanProperty visibleProperty()
	{
		return this.visible;
	}

	public ReadOnlyObjectProperty< DataSource< D, T > > dataSourceProperty()
	{
		return this.dataSource;
	}

	public ObjectProperty< Function< D, Converter< D, BoolType > > > maskGeneratorProperty()
	{
		return this.maskGenerator;
	}

	public ObjectProperty< TYPE > typeProperty()
	{
		return this.type;
	}

	public static class LabelSourceState< T, D > extends AtlasSourceState< T, D >
	{

		public LabelSourceState( final DataSource< D, T > dataSource )
		{
			super.dataSource.set( dataSource );
			typeProperty().set( TYPE.RAW );
		}

		private final ObjectProperty< FragmentSegmentAssignmentState< ? > > assignment = new SimpleObjectProperty<>();

		private final ObjectProperty< ToIdConverter > toIdConverter = new SimpleObjectProperty<>();

		private final ObservableMap< Mode, ARGBStream > streams = FXCollections.observableHashMap();

		private final ObservableMap< Mode, SelectedIds > selectedIds = FXCollections.observableHashMap();

		public ObjectProperty< FragmentSegmentAssignmentState< ? > > assignmentProperty()
		{
			return this.assignment;
		}

		public ObjectProperty< ToIdConverter > toIdConverterProperty()
		{
			return this.toIdConverter;
		}

		public ObservableMap< Mode, ARGBStream > streams()
		{
			return this.streams;
		}

		public ObservableMap< Mode, SelectedIds > selectedIds()
		{
			return this.selectedIds;
		}
	}

	public static class RawSourceState< T extends RealType< T >, D > extends AtlasSourceState< T, D >
	{

		private final DoubleProperty min = new SimpleDoubleProperty( 0.0 );

		private final DoubleProperty max = new SimpleDoubleProperty( 1.0 );

		private final DoubleProperty selectionMin = new SimpleDoubleProperty( 0.0 );

		private final DoubleProperty selectionMax = new SimpleDoubleProperty( 1.0 );

		private final ObjectProperty< Color > color = new SimpleObjectProperty<>( Color.WHITE );

		public RawSourceState( final DataSource< D, T > dataSource, final double min, final double max )
		{
			final RealARGBColorConverter.Imp0< T > conv = new RealARGBColorConverter.Imp0<>( min, max );
			this.selectionMin.addListener( ( obs, oldv, newv ) -> this.min.set( this.min.get() < newv.doubleValue() ? newv.doubleValue() : this.min.get() ) );
			this.selectionMax.addListener( ( obs, oldv, newv ) -> this.max.set( this.max.get() > newv.doubleValue() ? newv.doubleValue() : this.max.get() ) );
			this.minProperty().addListener( ( obs, oldv, newv ) -> conv.setMin( newv.doubleValue() ) );
			this.maxProperty().addListener( ( obs, oldv, newv ) -> conv.setMax( newv.doubleValue() ) );
			this.colorProperty().addListener( ( obs, oldv, newv ) -> conv.setColor( toARGBType( newv ) ) );
			setConverter( conv );
			super.dataSource.set( dataSource );
			typeProperty().set( TYPE.RAW );
		}

		public DoubleProperty minProperty()
		{
			return this.min;
		}

		public DoubleProperty maxProperty()
		{
			return this.max;
		}

		public DoubleProperty selectionMinProperty()
		{
			return this.selectionMin;
		}

		public DoubleProperty selectionMaxProperty()
		{
			return this.selectionMax;
		}

		public ObjectProperty< Color > colorProperty()
		{
			return this.color;
		}

		private static ARGBType toARGBType( final Color color )
		{
			return new ARGBType(
					( int ) ( color.getOpacity() * 255 + 0.5 ) << 24 |
							( int ) ( color.getRed() * 255 + 0.5 ) << 16 |
							( int ) ( color.getGreen() * 255 + 0.5 ) << 8 |
							( int ) ( color.getBlue() * 255 + 0.5 ) << 0 );
		}

	}

}
