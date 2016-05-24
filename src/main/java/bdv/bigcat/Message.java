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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 *
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class Message
{
	static public enum MessageType
	{
		MERGE( "merge" ),
		SEPARATE( "separate" ),
		ISOLATE( "isolate" ),
		FRAGMENT_SEGMENT_LUT( "fragment-segment-lut" ),
		HANDSHAKE( "handshake" );

		private final String name;

		private MessageType( final String name )
		{
			this.name = name;
		}

		public boolean equalsName( final String otherName )
		{
			return ( otherName == null ) ? false : name.equals( otherName );
		}

		@Override
		public String toString()
		{
			return this.name;
		}
	}

	static public class MergeData
	{
		public long[] fragments;
	}

	static public class SeparateData
	{
		public long fragmentA;
		public long fragmentB;
	}

	static public class IsolateData
	{
		public long fragment;
	}

	static public class FragmentSegmentLUTData
	{
		public long[] fragments;
		public long[] segments;
	}

	protected Object data = null;


	public Message() {}


	public Message( final Object data )
	{
		this.data = data;
	}


	public MessageType getType()
	{
		return typeForObj( data );
	}


	public Object getData()
	{
		return data;
	}


	static public MessageType typeForStr( final String typeName )
	{
		if ( typeName != null )
		{
			if ( MessageType.MERGE.equalsName( typeName ) )
				return MessageType.MERGE;
			if ( MessageType.SEPARATE.equalsName( typeName ) )
				return MessageType.SEPARATE;
			if ( MessageType.ISOLATE.equalsName( typeName ) )
				return MessageType.ISOLATE;
			if ( MessageType.FRAGMENT_SEGMENT_LUT.equalsName( typeName ) )
				return MessageType.FRAGMENT_SEGMENT_LUT;
		}
		return MessageType.HANDSHAKE;
	}


	static public MessageType typeForObj( final Object data )
	{
		if ( data != null )
		{
			if ( MergeData.class.isInstance( data ) )
				return MessageType.MERGE;
			if ( SeparateData.class.isInstance( data ) )
				return MessageType.SEPARATE;
			if ( IsolateData.class.isInstance( data ) )
				return MessageType.ISOLATE;
			if ( FragmentSegmentLUTData.class.isInstance( data ) )
				return MessageType.FRAGMENT_SEGMENT_LUT;
		}
		return MessageType.HANDSHAKE;
	}


	static public class Serializer implements JsonSerializer< Message >
	{
		@SuppressWarnings( "incomplete-switch" )
		@Override
		public JsonElement serialize( final Message src, final Type typeOfSrc, final JsonSerializationContext context )
		{
			final JsonObject json = new JsonObject();
			final MessageType type = typeForObj( src.data );
			json.addProperty( "type", type.toString() );

			switch ( type )
			{
			case MERGE:
			case SEPARATE:
			case ISOLATE:
			case FRAGMENT_SEGMENT_LUT:
				json.add( "data", context.serialize( src.data ) );
			}

			return json;
		}
	}


	static public class GSONDeserializer implements JsonDeserializer< Message >
	{
		@SuppressWarnings( "incomplete-switch" )
		@Override
		public Message deserialize( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
		{
			final JsonObject jsonObject = json.getAsJsonObject();

			Object data = null;

			if ( jsonObject.has( "type" ) )
			{
				final JsonElement typeJsonElement = jsonObject.get( "type" );
				final MessageType type = typeForStr( typeJsonElement.getAsString() );

				if ( jsonObject.has( "data" ) )
				{
					final JsonObject dataJsonObject = jsonObject.getAsJsonObject( "data" );
					switch ( type )
					{
					case MERGE:
						data = context.deserialize( dataJsonObject, MergeData.class );
						break;
					case SEPARATE:
						data = context.deserialize( dataJsonObject, SeparateData.class );
						break;
					case ISOLATE:
						data = context.deserialize( dataJsonObject, IsolateData.class );
						break;
					case FRAGMENT_SEGMENT_LUT:
						data = context.deserialize( dataJsonObject, FragmentSegmentLUTData.class );
						break;
					}
				}
			}

			return new Message( data );
		}
	}
}
