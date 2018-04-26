package org.janelia.saalfeldlab.paintera.meshes.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.janelia.saalfeldlab.paintera.meshes.Interruptible;
import org.janelia.saalfeldlab.util.HashWrapper;

import net.imglib2.RandomAccess;
import net.imglib2.cache.CacheLoader;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.LazyCellImg.LazyCells;
import net.imglib2.type.label.VolatileLabelMultisetArray;

public class UniqueLabelListLabelMultisetCacheLoader implements CacheLoader< HashWrapper< long[] >, long[] >, Interruptible< HashWrapper< long[] > >
{

	private final List< Consumer< HashWrapper< long[] > > > interruptListeners = new ArrayList<>();

	final LazyCells< Cell< VolatileLabelMultisetArray > > cells;

	public UniqueLabelListLabelMultisetCacheLoader( final LazyCells< Cell< VolatileLabelMultisetArray > > cells )
	{
		super();
		this.cells = cells;
	}

	@Override
	public void interruptFor( final HashWrapper< long[] > t )
	{
		this.interruptListeners.forEach( l -> l.accept( t ) );
	}

	@Override
	public long[] get( final HashWrapper< long[] > key ) throws Exception
	{

		final boolean[] interrupted = { false };
		final Consumer< HashWrapper< long[] > > listener = interrupedKey -> {
			if ( interrupedKey.equals( key ) )
				interrupted[ 0 ] = true;
		};
		this.interruptListeners.add( listener );
		try
		{
			final long[] cellGridPosition = key.getData();
			final RandomAccess< Cell< VolatileLabelMultisetArray > > access = cells.randomAccess();
			access.setPosition( cellGridPosition );
			return access.get().getData().containedLabels();
		}
		finally
		{
			this.interruptListeners.remove( listener );
		}
	}

}
