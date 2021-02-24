package org.janelia.saalfeldlab.paintera.data.n5

import java.lang.reflect.Field

class ReflectionHelpers {

    companion object {
        @Throws(NoSuchFieldException::class)
        @JvmStatic
        fun searchForField(
            startClass: Class<*>,
            name: String
        ): Field {

            return try {
                startClass.getDeclaredField(name).also { it.isAccessible = true }
            } catch (e: NoSuchFieldException) {
                val superClass = startClass.superclass ?: throw e
                searchForField(superClass, name)
            }

        }
    }
}
