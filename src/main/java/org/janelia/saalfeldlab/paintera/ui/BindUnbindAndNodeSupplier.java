package org.janelia.saalfeldlab.paintera.ui;

import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.layout.Pane;

public interface BindUnbindAndNodeSupplier extends Supplier<Node>, BindUnbind
{

	public static BindUnbindAndNodeSupplier empty()
	{
		return new Empty();
	}

	public static BindUnbindAndNodeSupplier noBind(Supplier<Node> n) { return new NoBind(n); };

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

	public static class NoBind implements BindUnbindAndNodeSupplier
	{

		private final Supplier<Node> n;

		public NoBind(Supplier<Node> n)
		{
			this.n = n;
		}

		@Override
		public Node get()
		{
			return n.get();
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
