package bdv.bigcat.viewer.atlas.source;

import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

public class CloseButton
{

	public static Node create( final double size )
	{
		return create( path -> path.setStrokeWidth( 1 ), size );
	}

	public static Node create( final Consumer< Path > pathSetup, final double size )
	{
		final StackPane closeBtn = new StackPane();
		final Path path = new Path();
		pathSetup.accept( path );
		path.getElements().addAll(
				new MoveTo( 0, 0 ),
				new LineTo( size, size ),
				new MoveTo( 0, size ),
				new LineTo( size, 0 ) );
		closeBtn.getChildren().add( path );
		return closeBtn;
	}

}
