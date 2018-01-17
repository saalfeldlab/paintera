package bdv.bigcat.viewer.atlas.source;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;

public class DoubleFilter implements UnaryOperator< TextFormatter.Change >
{

	private final Pattern validEditingState = Pattern.compile( "-?(([1-9][0-9]*)|0)?(\\.[0-9]*)?" );

	@Override
	public Change apply( final Change c )
	{
		final String text = c.getControlNewText();
		if ( validEditingState.matcher( text ).matches() )
			return c;
		else
			return null;
	}

}
