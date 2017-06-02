package bdv.bigcat.viewer;

import java.util.ArrayList;
import java.util.Arrays;

import bdv.viewer.SourceAndConverter;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import net.imglib2.converter.Converter;
import net.imglib2.converter.RealARGBConverter;
import net.imglib2.type.numeric.ARGBType;

public class SetMinMaxCell extends ListCell< SourceAndConverter< ? > >
{

	public static interface UpdateListener
	{

		public void contrastChanged();

	}

	private final GridPane gridPane;

	private final TextField min;

	private final TextField max;

	private final Label label;

	private final Button visibilityButton;

	private final ArrayList< SetMinMaxCell.UpdateListener > listeners;

	private void updateMinAndNotify( final RealARGBConverter< ? > conv, final double min )
	{
		conv.setMin( min );
		listeners.forEach( UpdateListener::contrastChanged );
	}

	private void updateMaxAndNotify( final RealARGBConverter< ? > conv, final double max )
	{
		conv.setMax( max );
		listeners.forEach( UpdateListener::contrastChanged );
	}

	@Override
	public void updateItem( final SourceAndConverter< ? > sac, final boolean empty )
	{

		super.updateItem( sac, empty );
		if ( empty || sac == null )
		{
			setGraphic( null );
			setText( null );
			this.gridPane.getChildren().clear();
		}

		else
		{

			this.gridPane.getChildren().clear();

			int rowIndex = 0;

			this.label.setText( sac.getSpimSource().getName() );
			this.visibilityButton.setText( "\uD83D\uDC41bla" );
			this.gridPane.add( this.label, 0, rowIndex );
			this.gridPane.add( this.visibilityButton, 1, rowIndex );

			++rowIndex;

			if ( sac.getConverter() instanceof RealARGBConverter )
			{
				final Converter< ?, ARGBType > conv = sac.getConverter();
				this.min.setText( ( ( RealARGBConverter ) conv ).getMin() + "" );
				this.max.setText( ( ( RealARGBConverter ) conv ).getMax() + "" );
				this.min.setDisable( false );
				this.max.setDisable( false );
				this.min.setOnAction( e -> updateMinAndNotify( ( RealARGBConverter ) conv, Double.parseDouble( this.min.getText() ) ) );
				this.max.setOnAction( e -> updateMaxAndNotify( ( RealARGBConverter ) conv, Double.parseDouble( this.max.getText() ) ) );
				this.gridPane.add( this.min, 0, rowIndex );
				this.gridPane.add( this.max, 1, rowIndex );
				++rowIndex;
			}
			else
			{
				this.min.setText( "" );
				this.max.setText( "" );
				this.min.setDisable( true );
				this.max.setDisable( true );
				this.min.setOnAction( e -> {} );
				this.max.setOnAction( e -> {} );
			}
			setGraphic( gridPane );
		}
	}

	public SetMinMaxCell( final SetMinMaxCell.UpdateListener... listeners )
	{
		super();

		this.min = new TextField();
		this.max = new TextField();
		this.label = new Label();
		this.visibilityButton = new Button();
		this.visibilityButton.setBackground( Background.EMPTY );
		this.gridPane = new GridPane();
		setGraphic( gridPane );

		this.listeners = new ArrayList<>( Arrays.asList( listeners ) );

		final String pattern = "[0-9]*\\.?[0-9]*";

		this.min.textProperty().addListener( ( ChangeListener< String > ) ( observable, oldValue, newValue ) -> {
			if ( !newValue.matches( pattern ) )
				this.min.setText( oldValue );
		} );

		this.max.textProperty().addListener( ( ChangeListener< String > ) ( observable, oldValue, newValue ) -> {
			if ( !newValue.matches( pattern ) )
				this.max.setText( oldValue );
		} );

	}

}