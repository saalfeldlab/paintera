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

import java.lang.reflect.Type;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import gnu.trove.iterator.TLongLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

/**
 *
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class SegmentBodyAssignment
{
	/**
	 * Serializes {@link SegmentBodyAssignment} into JSON of the form
	 * <pre>
	 * {
	 *   "lut" : {
	 *     "<segment_id1>" : <object_id1>,
	 *     "<segment_id2>" : <object_id2>,
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	static public class SegmentBodySerializer implements JsonSerializer< SegmentBodyAssignment >
	{
		@Override
		public JsonElement serialize( final SegmentBodyAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final JsonObject jsonLut = new JsonObject();
			final TLongLongIterator lutIterator = src.lut.iterator();
			while ( lutIterator.hasNext() )
			{
				lutIterator.advance();
				jsonLut.addProperty( Long.toString( lutIterator.key() ), lutIterator.value() );
			}

			final JsonObject jsonObject = new JsonObject();
			jsonObject.add( "lut", jsonLut );

			return jsonObject;
		}
	}

	/**
	 * Serializes {@link SegmentBodyAssignment} into JSON of the form
	 * <pre>
	 * {
	 *   "segments" : [<segment_id1>, <segment_id2>, ...],
	 *   "objects" : [<object_id1>, <object_id2>, ...]
	 *   }
	 * }
	 * </pre>
	 *
	 * TODO number of elements and bodies is limited by implementation to
	 * 2<sup>31</sup> and most likely significantly less due to memory
	 * consumption on {@link JsonPrimitive} creation per each number.
	 */
	static public class SegmentBodyListSerializer implements JsonSerializer< SegmentBodyAssignment >
	{
		@Override
		public JsonElement serialize( final SegmentBodyAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final JsonArray segments = new JsonArray();
			final JsonArray bodies = new JsonArray();
			final TLongLongIterator lutIterator = src.lut.iterator();
			while ( lutIterator.hasNext() )
			{
				lutIterator.advance();
				segments.add( new JsonPrimitive( lutIterator.key() ) );
				bodies.add( new JsonPrimitive( lutIterator.value() ) );
			}

			final JsonObject jsonObject = new JsonObject();
			jsonObject.add( "segments", segments );
			jsonObject.add( "bodies", bodies );

			return jsonObject;
		}
	}

	/**
	 * Serializes {@link SegmentBodyAssignment} into JSON of the form
	 * <pre>
	 * {
	 *   "ilut" : {
	 *     "<object_id1>" : [<segment_id1>, <segment_id2>, ...],
	 *     "<object_id2>" : [<segment_id3>, <segment_id4>, ...],
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	static public class BodySegmentsSerializer implements JsonSerializer< SegmentBodyAssignment >
	{
		@Override
		public JsonElement serialize( final SegmentBodyAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final Gson gson = new Gson();

			final JsonObject jsonILut = new JsonObject();
			final TLongObjectIterator< long[] > ilutIterator = src.ilut.iterator();
			while ( ilutIterator.hasNext() )
			{
				ilutIterator.advance();
				jsonILut.add(
						Long.toString( ilutIterator.key() ),
						gson.toJsonTree( ilutIterator.value() ) );
			}

			final JsonObject jsonObject = new JsonObject();
			jsonObject.add( "ilut", jsonILut );

			return jsonObject;
		}
	}

	/**
	 * Deserializes the various serializations of SegmentBodyAssignment.
	 *
	 * TODO number of elements and bodies is limited by implementation to
	 * 2<sup>31</sup> due to collecting them in a long[].
	 *
	 */
	static public class GSONDeserializer implements JsonDeserializer< SegmentBodyAssignment >
	{
		@Override
		public SegmentBodyAssignment deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
		{
			final JsonObject jsonObject = json.getAsJsonObject();

			boolean notYetDone = true;
			long[] segments = null;
			long[] bodies = null;
			String message = "Could not find either lut, ilut, or segments + bodies properties.";
			if ( jsonObject.has( "lut" ) )
			{
				final TLongArrayList segmentsList = new TLongArrayList();
				final TLongArrayList bodiesList = new TLongArrayList();

				final JsonElement lutJsonElement = jsonObject.get( "lut" );
				if ( lutJsonElement.isJsonObject() )
				{
					final Set< Entry< String, JsonElement > > lutJsonEntrySet = lutJsonElement.getAsJsonObject().entrySet();
					notYetDone = false;
					for ( final Entry< String, JsonElement > entry : lutJsonEntrySet )
					{
						try
						{
							final long segmentId = Long.parseLong( entry.getKey() );
							final JsonElement value = entry.getValue();
							if ( value.isJsonPrimitive() )
							{
								final long bodyId = value.getAsLong();
								segmentsList.add( segmentId );
								bodiesList.add( bodyId );
							}
							else
							{
								notYetDone = true;
								break;
							}
						}
						catch ( final Exception e )
						{
							notYetDone = true;
							message = e.getMessage();
						}
					}
					if ( !notYetDone )
					{
						segments = segmentsList.toArray();
						bodies = bodiesList.toArray();
					}
				}
			}
			if ( notYetDone && jsonObject.has( "ilut" ) )
			{
				final TLongArrayList segmentsList = new TLongArrayList();
				final TLongArrayList bodiesList = new TLongArrayList();

				final JsonElement ilutJsonElement = jsonObject.get( "lut" );
				if ( ilutJsonElement.isJsonObject() )
				{
					final Set< Entry< String, JsonElement > > ilutJsonEntrySet = ilutJsonElement.getAsJsonObject().entrySet();
					notYetDone = false;
A:					for ( final Entry< String, JsonElement > entry : ilutJsonEntrySet )
					{
						try
						{
							final long bodyId = Long.parseLong( entry.getKey() );
							final JsonElement value = entry.getValue();
							if ( value.isJsonArray() )
							{
								for ( final JsonElement segment : value.getAsJsonArray() )
								{
									if ( segment.isJsonPrimitive() )
									{
										final long segmentId = segment.getAsLong();
										segmentsList.add( segmentId );
										bodiesList.add( bodyId );
									}
									else
									{
										notYetDone = true;
										break A;
									}
								}
							}
							else
							{
								notYetDone = true;
								break;
							}
						}
						catch ( final Exception e )
						{
							notYetDone = true;
							message = e.getMessage();
						}
					}
				}
			}
			if ( notYetDone && jsonObject.has( "segments" ) && jsonObject.has( "bodies" ) )
			{
				final Gson gson = new Gson();
				notYetDone = false;
				try
				{
					segments = gson.fromJson( jsonObject.get( "segments" ), long[].class );
					bodies = gson.fromJson( jsonObject.get( "bodies" ), long[].class );
				}
				catch ( final Exception e )
				{
					notYetDone = true;
					message = e.getMessage();
				}
			}
			if ( notYetDone )
				throw new JsonParseException( message );
			else
				return new SegmentBodyAssignment( segments, bodies );
		}
	}

	final protected TLongLongHashMap lut = new TLongLongHashMap();
	final protected TLongObjectHashMap< long[] > ilut = new TLongObjectHashMap< long[] >();

	public SegmentBodyAssignment() {}

	public SegmentBodyAssignment( final long[] segments, final long[] bodies )
	{
		assert segments.length == bodies.length : "segments and bodies must be of same length";

		for ( int i = 0; i < segments.length; ++i )
			lut.put( segments[ i ], bodies[ i ] );

		syncILut();
	}
	
	public void initLut( final TLongLongHashMap lut )
	{
		this.lut.clear();
		this.ilut.clear();
		this.lut.putAll( lut );
		syncILut();
		
		System.out.println( "Done" );
	}

	/**
	 * Synchronize the inverse Lookup (body > [segments]) with the current
	 * forward lookup (segment > body)).  The current state of the inverse
	 * lookup will be cleared.
	 */
	protected void syncILut()
	{
		ilut.clear();
		final TLongLongIterator lutIterator =  lut.iterator();
		while ( lutIterator.hasNext() )
		{
			lutIterator.advance();
			final long segmentId = lutIterator.key();
			final long bodyId = lutIterator.value();
			long[] segments = ilut.get( bodyId );
			if ( segments == null )
				segments = new long[]{ segmentId };
			else
				segments= ArrayUtils.add( segments, segmentId );
			ilut.put( bodyId, segments);
		}
	}

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
