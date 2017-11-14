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
package bdv.bigcat.label;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import bdv.labels.labelset.Label;
import bdv.util.IdService;
import bdv.util.LocalIdService;
import gnu.trove.impl.Constants;
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
public class FragmentSegmentAssignment
{

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	/**
	 * Serializes {@link FragmentSegmentAssignment} into JSON of the form
	 *
	 * <pre>
	 * {
	 *   "lut" : {
	 *     "<fragment_id1>" : <segment_id1>,
	 *     "<fragment_id2>" : <segment_id2>,
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	static public class FragmentSegmentSerializer implements JsonSerializer< FragmentSegmentAssignment >
	{
		@Override
		public JsonElement serialize( final FragmentSegmentAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
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
	 * Serializes {@link FragmentSegmentAssignment} into JSON of the form
	 *
	 * <pre>
	 * {
	 *   "fragments" : [<segment_id1>, <segment_id2>, ...],
	 *   "segments" : [<object_id1>, <object_id2>, ...]
	 *   }
	 * }
	 * </pre>
	 *
	 * TODO number of elements and bodies is limited by implementation to
	 * 2<sup>31</sup> and most likely significantly less due to memory
	 * consumption on {@link JsonPrimitive} creation per each number.
	 */
	static public class SegmentBodyListSerializer implements JsonSerializer< FragmentSegmentAssignment >
	{
		@Override
		public JsonElement serialize( final FragmentSegmentAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final JsonArray fragments = new JsonArray();
			final JsonArray segments = new JsonArray();
			final TLongLongIterator lutIterator = src.lut.iterator();
			while ( lutIterator.hasNext() )
			{
				lutIterator.advance();
				fragments.add( new JsonPrimitive( lutIterator.key() ) );
				segments.add( new JsonPrimitive( lutIterator.value() ) );
			}

			final JsonObject jsonObject = new JsonObject();
			jsonObject.add( "fragments", fragments );
			jsonObject.add( "segments", segments );

			return jsonObject;
		}
	}

	/**
	 * Serializes {@link FragmentSegmentAssignment} into JSON of the form
	 *
	 * <pre>
	 * {
	 *   "ilut" : {
	 *     "<segment_id1>" : [<fragment_id1>, <fragment_id2>, ...],
	 *     "<segment_id2>" : [<fragment_id3>, <fragment_id4>, ...],
	 *     ...
	 *   }
	 * }
	 * </pre>
	 */
	static public class BodySegmentsSerializer implements JsonSerializer< FragmentSegmentAssignment >
	{
		@Override
		public JsonElement serialize( final FragmentSegmentAssignment src, final Type typeOfSrc, final JsonSerializationContext context )
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
	 * Deserializes the various serializations of FragmentSegmentAssignment.
	 *
	 * TODO number of fragments and segments is limited by implementation to
	 * 2<sup>31</sup> due to collecting them in a long[].
	 *
	 */
	static public class GSONDeserializer implements JsonDeserializer< FragmentSegmentAssignment >
	{
		@Override
		public FragmentSegmentAssignment deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
		{
			final JsonObject jsonObject = json.getAsJsonObject();

			boolean notYetDone = true;
			long[] fragments = null;
			long[] segments = null;
			String message = "Could not find either lut, ilut, or segments + bodies properties.";
			if ( jsonObject.has( "lut" ) )
			{
				final TLongArrayList fragmentsList = new TLongArrayList();
				final TLongArrayList segmentsList = new TLongArrayList();

				final JsonElement lutJsonElement = jsonObject.get( "lut" );
				if ( lutJsonElement.isJsonObject() )
				{
					final Set< Entry< String, JsonElement > > lutJsonEntrySet = lutJsonElement.getAsJsonObject().entrySet();
					notYetDone = false;
					for ( final Entry< String, JsonElement > entry : lutJsonEntrySet )
						try
						{
							final long fragmentId = Long.parseLong( entry.getKey() );
							final JsonElement value = entry.getValue();
							if ( value.isJsonPrimitive() )
							{
								final long segmentId = value.getAsLong();
								fragmentsList.add( fragmentId );
								segmentsList.add( segmentId );
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
					if ( !notYetDone )
					{
						fragments = fragmentsList.toArray();
						segments = segmentsList.toArray();
					}
				}
			}
			if ( notYetDone && jsonObject.has( "ilut" ) )
			{
				final TLongArrayList fragmentsList = new TLongArrayList();
				final TLongArrayList segmentsList = new TLongArrayList();

				final JsonElement ilutJsonElement = jsonObject.get( "lut" );
				if ( ilutJsonElement.isJsonObject() )
				{
					final Set< Entry< String, JsonElement > > ilutJsonEntrySet = ilutJsonElement.getAsJsonObject().entrySet();
					notYetDone = false;
					A: for ( final Entry< String, JsonElement > entry : ilutJsonEntrySet )
						try
						{
							final long segmentId = Long.parseLong( entry.getKey() );
							final JsonElement value = entry.getValue();
							if ( value.isJsonArray() )
							{
								for ( final JsonElement fragment : value.getAsJsonArray() )
									if ( fragment.isJsonPrimitive() )
									{
										final long fragmentId = fragment.getAsLong();
										fragmentsList.add( fragmentId );
										segmentsList.add( segmentId );
									}
									else
									{
										notYetDone = true;
										break A;
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
			if ( notYetDone && jsonObject.has( "fragments" ) && jsonObject.has( "segments" ) )
			{
				final Gson gson = new Gson();
				notYetDone = false;
				try
				{
					fragments = gson.fromJson( jsonObject.get( "fragments" ), long[].class );
					segments = gson.fromJson( jsonObject.get( "segments" ), long[].class );
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
			{
				final LocalIdService idService = new LocalIdService();
				final long maxId = IdService.max( IdService.max( fragments ), IdService.max( segments ) );
				idService.setNext( maxId + 1 );
				return new FragmentSegmentAssignment( fragments, segments, idService );
			}
		}
	}

	final protected TLongLongHashMap lut = new TLongLongHashMap( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT, Label.TRANSPARENT );

	final protected TLongObjectHashMap< long[] > ilut = new TLongObjectHashMap<>( Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Label.TRANSPARENT );

	protected IdService idService;

	public FragmentSegmentAssignment( final IdService idService )
	{
		this.idService = idService;
	}

	public FragmentSegmentAssignment( final long[] fragments, final long[] segments, final IdService idService )
	{
		assert fragments.length == segments.length: "segments and bodies must be of same length";

		for ( int i = 0; i < fragments.length; ++i )
			lut.put( fragments[ i ], segments[ i ] );

		this.idService = idService;

		syncILut();
	}

	public TLongLongHashMap getLut()
	{
		return lut;
	}

	public void setIdService( final IdService idService )
	{
		this.idService = idService;
	}

	public void initLut( final TLongLongHashMap lut )
	{
		this.lut.clear();
		this.ilut.clear();
		this.lut.putAll( lut );
		syncILut();

		LOG.debug( "Done with initLut" );
	}

	/**
	 * Synchronize the inverse Lookup (segment > [fragments]) with the current
	 * forward lookup (fragment > segment)). The current state of the inverse
	 * lookup will be cleared.
	 */
	protected void syncILut()
	{
		ilut.clear();
		final TLongLongIterator lutIterator = lut.iterator();
		while ( lutIterator.hasNext() )
		{
			lutIterator.advance();
			final long fragmentId = lutIterator.key();
			final long segmentId = lutIterator.value();
			long[] fragments = ilut.get( segmentId );
			if ( fragments == null )
				fragments = new long[] { fragmentId };
			else
				fragments = ArrayUtils.add( fragments, fragmentId );
			ilut.put( segmentId, fragments );
		}
	}

	/**
	 * Get the body that is assigned to a fragment id.
	 *
	 * @param id
	 */
	public long getSegment( final long fragmentId )
	{
		final long id;
		synchronized ( this )
		{
			final long segmentId = lut.get( fragmentId );
			if ( segmentId == lut.getNoEntryValue() )
			{
				id = fragmentId;
				lut.put( fragmentId, id );
				ilut.put( id, new long[] { fragmentId } );
			}
			else
				id = segmentId;
		}
		return id;
	}

	/**
	 * Get the segments assigned to a body.
	 *
	 * @param id
	 */
	public long[] getFragments( final long segmentId )
	{
		final long[] fragments;
		synchronized ( this )
		{
			fragments = ilut.get( segmentId );
		}
		return fragments;
	}

	/**
	 * Assign all fragments of segmentId1 to segmentId2.
	 *
	 * @param segmentId1
	 * @param segmentId2
	 */
	public void assignFragments( final long segmentId1, final long segmentId2 )
	{
		if ( segmentId1 == segmentId2 )
			return;

		synchronized ( this )
		{
			final long[] fragments1 = ilut.get( segmentId1 );
			final long[] fragments2 = ilut.get( segmentId2 );
			for ( final long fragmentId : fragments1 )
				lut.put( fragmentId, segmentId2 );
			ilut.put( segmentId2, ArrayUtils.addAll( fragments1, fragments2 ) );
			ilut.remove( segmentId1 );
		}
	}

	/**
	 * Merge two segments.
	 *
	 * @param segmentId1
	 * @param segmentId2
	 */
	public void mergeSegments( final long segmentId1, final long segmentId2 )
	{
		if ( segmentId1 == segmentId2 )
			return;

		final long mergedSegmentId = idService.next();
		synchronized ( this )
		{
			final long[] fragments1 = ilut.get( segmentId1 );
			final long[] fragments2 = ilut.get( segmentId2 );
			final long[] fragments = ArrayUtils.addAll( fragments1, fragments2 );
			for ( final long fragmentId : fragments )
				lut.put( fragmentId, mergedSegmentId );
			ilut.put( mergedSegmentId, fragments );
			ilut.remove( segmentId1 );
			ilut.remove( segmentId2 );
		}
	}

	/**
	 * Merge two segments assigned to two fragment ids.
	 *
	 * @param fragmentId1
	 * @param fragmentId2
	 */
	public void mergeFragmentSegments( final long fragmentId1, final long fragmentId2 )
	{
		final long segmentId1, segmentId2;
		segmentId1 = getSegment( fragmentId1 );
		segmentId2 = getSegment( fragmentId2 );
		mergeSegments( segmentId1, segmentId2 );
	}

	/**
	 * Detach a segment from the body that it has been associated with
	 *
	 * @param fragmentId
	 */
	public void detachFragment( final long fragmentId )
	{
		synchronized ( this )
		{
			final long segmentId = lut.get( fragmentId );
			final long[] fragments = ilut.get( segmentId );
			if ( fragments != null && fragments.length > 1 )
			{
				final long[] newFragments = ArrayUtils.removeElement( fragments, fragmentId );
				ilut.put( segmentId, newFragments );

				final long newSegmentId = fragmentId;
				lut.put( fragmentId, newSegmentId );
				ilut.put( newSegmentId, new long[] { fragmentId } );
			}
		}
	}
}
