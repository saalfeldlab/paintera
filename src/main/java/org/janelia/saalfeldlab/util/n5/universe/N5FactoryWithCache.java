/**
 * Copyright (c) 2017-2021, Saalfeld lab, HHMI Janelia
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * <p>
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * <p>
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.util.n5.universe;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import java.io.IOException;
import java.util.HashMap;

/**
 * Factory for various N5 readers and writers.  Implementation specific
 * parameters can be provided to the factory instance and will be used when
 * such implementations are generated and ignored otherwise. Reasonable
 * defaults are provided.
 *
 * @author Stephan Saalfeld
 * @author John Bogovic
 * @author Igor Pisarev
 */
public class N5FactoryWithCache extends N5Factory {

	private final HashMap<String, N5Writer> writerCache = new HashMap<>();
	private final HashMap<String, N5Reader> readerCache = new HashMap<>();

	@Override public N5Reader openReader(String url) {

		/* Check if writer is valid (it may have been closed by someone) */
		final N5Reader cachedContainer = readerCache.get(url);
		if (cachedContainer != null) {
			try {
				cachedContainer.getVersion();
			} catch (Exception e) {
				readerCache.remove(url).close();
			}
		} else {
			final N5Reader reader = super.openReader(url);
			if (reader.getAttribute("/", N5Reader.VERSION_KEY, String.class) != null) {
				readerCache.put(url, reader);
			}
		}
		return readerCache.get(url);
	}

	@Override public N5Writer openWriter(String url)  {

		/* Check if writer is valid (it may have been closed by someone) */
		final N5Writer cachedContainer = writerCache.get(url);
		if (cachedContainer != null) {
			try {
				/* See if its open, and we still have write permissions */
				cachedContainer.setAttribute("/", N5Reader.VERSION_KEY, cachedContainer.getVersion().toString());
			} catch (Exception e) {
				writerCache.remove(url).close();
				if (readerCache.get(url) == cachedContainer) {
					readerCache.remove(url);
				}
			}
		} else {
			final N5Writer n5Writer = super.openWriter(url);
			/* See if we have write permissions before we declare success */
			n5Writer.setAttribute("/", N5Reader.VERSION_KEY, n5Writer.getVersion());
			writerCache.put(url, n5Writer);
			if (readerCache.get(url) != null) {
				readerCache.remove(url).close();
			}
			readerCache.put(url, n5Writer);
		}
		return writerCache.get(url);
	}

	public void clearKey(String url) {

		final var writer = writerCache.remove(url);
		if (writer != null)
			writer.close();

		final var reader = readerCache.remove(url);
		if (reader != null)
			reader.close();
	}

	public void clearCache() {
		writerCache.clear();
		readerCache.clear();
	}

	public N5Reader getFromCache(String url) {
		return readerCache.get(url);
	}
}
