package org.janelia.saalfeldlab.paintera.data.n5;

import java.lang.reflect.Field;

public class ReflectionHelpers
{

	public static Field searchForField(
			final Class<?> startClass,
			final String name) throws NoSuchFieldException
	{

		try
		{
			final Field field = startClass.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (final NoSuchFieldException e)
		{
			final Class<?> superClass = startClass.getSuperclass();
			if (superClass == null)
				throw e;
			return searchForField(superClass, name);
		}
	}
}
