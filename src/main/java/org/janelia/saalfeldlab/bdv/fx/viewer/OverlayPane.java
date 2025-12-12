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
package org.janelia.saalfeldlab.bdv.fx.viewer;

import org.janelia.saalfeldlab.bdv.fx.viewer.render.OverlayRendererGeneric;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tobias Pietzsch
 * @author Philipp Hanslovsky
 */
public class OverlayPane extends StackPane {

	/**
	 * The {@link OverlayRendererGeneric} that draws on top of the current buffered image.
	 */
	final protected CopyOnWriteArrayList<OverlayRendererGeneric<GraphicsContext>> overlayRenderers;

	private final CanvasPane canvasPane = new CanvasPane(0, 0);

	private final ObservableList<Node> children = FXCollections.unmodifiableObservableList(super.getChildren());

	/**
	 *
	 */
	public OverlayPane() {

		super();
		super.getChildren().add(canvasPane);
		setBackground(new Background(new BackgroundFill(Color.BLACK.deriveColor(0.0, 1.0, 1.0, 0.0), CornerRadii.EMPTY, Insets.EMPTY)));

		this.overlayRenderers = new CopyOnWriteArrayList<>();

		final ChangeListener<Number> sizeChangeListener = (observable, oldValue, newValue) -> {
			final double wd = widthProperty().get();
			final double hd = heightProperty().get();
			final int w = (int)wd;
			final int h = (int)hd;
			if (w <= 0 || h <= 0)
				return;
			overlayRenderers.forEach(or -> or.setCanvasSize(w, h));
			layout();
			drawOverlays();
		};

		widthProperty().addListener(sizeChangeListener);
		heightProperty().addListener(sizeChangeListener);

	}

	public void drawOverlays() {

		Paintera.ifPaintable(() -> InvokeOnJavaFXApplicationThread.invoke(() -> {
			final Canvas canvas = canvasPane.getCanvas();
			final GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
			overlayRenderers.forEach(or -> or.drawOverlays(gc));
		}));

	}

	/**
	 * Add an {@link OverlayRendererGeneric} that draws on top of the current buffered image.
	 *
	 * @param renderer overlay renderer to add.
	 */
	public void addOverlayRenderer(final OverlayRendererGeneric<GraphicsContext> renderer) {

		if (!overlayRenderers.contains(renderer)) {
			overlayRenderers.add(renderer);
		}
		renderer.setCanvasSize((int)getWidth(), (int)getHeight());
	}

	/**
	 * Remove an {@link OverlayRendererGeneric}.
	 *
	 * @param renderer overlay renderer to remove.
	 */
	public void removeOverlayRenderer(final OverlayRendererGeneric<GraphicsContext> renderer) {

		overlayRenderers.remove(renderer);
	}

	@Override
	public ObservableList<Node> getChildren() {

		return this.children;
	}
}
