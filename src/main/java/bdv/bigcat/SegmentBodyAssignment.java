/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bdv.bigcat;

import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import org.apache.commons.lang.ArrayUtils;

/**
 *
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class SegmentBodyAssignment
{
	final protected TLongLongHashMap lut = new TLongLongHashMap();
	final protected TLongObjectHashMap< long[] > ilut = new TLongObjectHashMap< long[] >();

	/**
	 * Get the body that is assigned to a segment id.
	 *
	 * @param id
	 */
	public long getBody( final long segmentId )
	{
		final long id;
		synchronized ( lut )
		{
			final long bodyId = lut.get( segmentId );
			if ( bodyId == lut.getNoEntryValue() ) {
				lut.put( segmentId, segmentId );
				ilut.put( segmentId, new long[]{ segmentId } );
				id = segmentId;
			}
			else
				id = bodyId;
		}
		return id;
	}

	/**
	 * Get the segments assigned to a body.
	 *
	 * @param id
	 */
	public long[] getSegments( final long bodyId )
	{
		return ilut.get( bodyId );
	}

	/**
	 * Merge two bodies.
	 *
	 * @param bodyId1
	 * @param bodyId2
	 */
	public void mergeBodies( final long bodyId1, final long bodyId2 )
	{
		if ( bodyId1 == bodyId2 )
			return;

		synchronized ( ilut )
		{
			final long[] segments1 = getSegments( bodyId1 );
			final long[] segments2 = getSegments( bodyId2 );
			final long[] segments = ArrayUtils.addAll( segments1, segments2 );
			for ( final long segmentId : segments )
				lut.put( segmentId, bodyId1 );
			ilut.put( bodyId1, segments );
			ilut.remove( bodyId2 );
		}
	}

	/**
	 * Merge two bodies assigned to two segment ids.
	 *
	 * @param segmentId1
	 * @param segmentId2
	 */
	public void mergeSegmentBodies( final long segmentId1, final long segmentId2 )
	{
		final long bodyId1, bodyId2;
		synchronized ( ilut )
		{
			bodyId1 = getBody( segmentId1 );
			bodyId2 = getBody( segmentId2 );
		}
		mergeBodies( bodyId1, bodyId2 );
	}

	/**
	 * Detach a segment from the body that it has been associated with
	 *
	 * @param segmentId
	 */
	public void detachSegment( final long segmentId )
	{
		synchronized ( ilut )
		{
			final long bodyId = lut.get( segmentId );
			final long[] segments = ilut.get( bodyId );
			if ( segments.length > 1 )
			{
				final long[] newSegments = ArrayUtils.removeElement( segments, segmentId );
				ilut.put( bodyId, newSegments );

				final long newBodyId = bodyId == segmentId ? newSegments[ 0 ] : segmentId;
				lut.put( segmentId, newBodyId );
				ilut.put( newBodyId, new long[]{ segmentId } );
			}
		}
	}
}
