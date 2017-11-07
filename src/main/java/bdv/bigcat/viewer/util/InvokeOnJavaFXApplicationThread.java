/*-
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
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
package bdv.bigcat.viewer.util;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import javafx.application.Platform;

public class InvokeOnJavaFXApplicationThread
{
	public static void invoke( final Runnable runnable )
	{
		if ( Platform.isFxApplicationThread() )
			runnable.run();
		else
			Platform.runLater( runnable );
	}

	public static void invokeAndWait( final Runnable runnable ) throws InterruptedException
	{
		final CountDownLatch latch = new CountDownLatch( 1 );
		final Runnable countDownRunnable = () -> {
			runnable.run();
			latch.countDown();
		};
		invoke( countDownRunnable );
		synchronized ( latch )
		{
			latch.await();
		}
	}

	public static void invokeAndWait( final Runnable runnable, final Consumer< InterruptedException > exceptionHandler )
	{
		try
		{
			invokeAndWait( runnable );
		}
		catch ( final InterruptedException e )
		{
			exceptionHandler.accept( e );
		}
	}
}
