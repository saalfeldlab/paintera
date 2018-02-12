package bdv.bigcat.viewer.atlas.ui;

import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public interface BindUnbindAndNodeSupplier extends Supplier< Node >, BindUnbind
{

	public static BindUnbindAndNodeSupplier empty()
	{
		return new Empty();
	}

	public static class Empty implements BindUnbindAndNodeSupplier
	{

		@Override
		public Node get()
		{
			return new Pane();
		}

		@Override
		public void bind()
		{

		}

		@Override
		public void unbind()
		{

		}

	}
}
