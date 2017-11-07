/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.bigcat.viewer.bdvfx;

import java.awt.Component;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

import javax.swing.JComponent;

import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformListener;

/**
 * A component that uses one or several {@link OverlayRenderer OverlayRenderers}
 * to render a canvas displayed on screen. Current implementations derive from
 * {@link JComponent}.
 * <p>
 * {@link InteractiveDisplayCanvasGeneric} also owns a
 * {@link TransformEventHandler}, which is registered to listen to mouse and
 * keyboard events if it implements {@link MouseListener}, etc.
 * <p>
 * Moreover, {@link InteractiveDisplayCanvasGeneric} is a transform event
 * multi-caster. It receives {@link TransformListener#transformChanged(Object)
 * transformChanged} events (usually from its {@link TransformEventHandler}) and
 * propagates them to all registered listeners.
 *
 * @param <A>
 *            transform type
 *
 * @author Tobias Pietzsch
 */
public interface InteractiveDisplayCanvasGeneric< A, G, H > extends TransformListener< A >
{

	/**
	 * Add an {@link OverlayRenderer} that draws on top of the current buffered
	 * image.
	 *
	 * @param renderer
	 *            overlay renderer to add.
	 */
	public void addOverlayRenderer( final OverlayRendererGeneric< G > renderer );

	/**
	 * Remove an {@link OverlayRenderer}.
	 *
	 * @param renderer
	 *            overlay renderer to remove.
	 */
	public void removeOverlayRenderer( final OverlayRendererGeneric< G > renderer );

	/**
	 * Add a {@link TransformListener} to notify about view transformation
	 * changes.
	 *
	 * @param listener
	 *            the transform listener to add.
	 */
	public void addTransformListener( final TransformListener< A > listener );

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 *            the transform listener to remove.
	 */
	public void removeTransformListener( final TransformListener< A > listener );

	/**
	 * Add new event handler. Depending on the interfaces implemented by
	 * <code>handler</code> calls {@link Component#addKeyListener(KeyListener)},
	 * {@link Component#addMouseListener(MouseListener)},
	 * {@link Component#addMouseMotionListener(MouseMotionListener)},
	 * {@link Component#addMouseWheelListener(MouseWheelListener)}.
	 */
	public void addHandler( final H handler );

	/**
	 * Remove an event handler. Add new event handler. Depending on the
	 * interfaces implemented by <code>handler</code> calls
	 * {@link Component#removeKeyListener(KeyListener)},
	 * {@link Component#removeMouseListener(MouseListener)},
	 * {@link Component#removeMouseMotionListener(MouseMotionListener)},
	 * {@link Component#removeMouseWheelListener(MouseWheelListener)}.
	 */
	public void removeHandler( final H handler );

	/**
	 * Get the {@link TransformEventHandler} that handles mouse and key events
	 * to update our view transform.
	 *
	 * @return handles mouse and key events to update the view transform.
	 */
	public TransformEventHandler< A > getTransformEventHandler();

	/**
	 * Set the {@link TransformEventHandler} that handles mouse and key events
	 * to update our view transform.
	 *
	 * @param transformEventHandler
	 *            handler to use
	 */
	public void setTransformEventHandler( final TransformEventHandler< A > transformEventHandler );

//	public void repaint();
}
