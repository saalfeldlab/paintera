package org.janelia.saalfeldlab.paintera

import org.junit.jupiter.api.extension.*

/**
 * Annotation to inject a PainteraTestApplication instance into test method parameters.
 * Works exactly like @TempDir.
 * 
 * Usage:
 * ```kotlin
 * @Test
 * fun myTest(@PainteraTestApp app: PainteraTestApplication) {
 *     // use the app
 * }
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(PainteraTestApplicationExtension::class)
annotation class PainteraTestApp

/**
 * JUnit 5 extension that provides PainteraTestApplication instances as test method parameters
 * when annotated with @PainteraApp.
 */
class PainteraTestApplicationExtension : ParameterResolver, AfterEachCallback {
    
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(PainteraTestApplicationExtension::class.java)
        private const val APP_KEY = "painteraTestApplication"
    }
    
    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.isAnnotated(PainteraTestApp::class.java) &&
               parameterContext.parameter.type == PainteraTestApplication::class.java
    }
    
    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val store = extensionContext.getStore(NAMESPACE)
        
        // Create and cache the application instance per test method
        return store.getOrComputeIfAbsent(APP_KEY, {
            ApplicationTestUtils.painteraTestApp()
        }, PainteraTestApplication::class.java)
    }
    
    override fun afterEach(context: ExtensionContext) {
        // Clean up the application after each test
        val store = context.getStore(NAMESPACE)
        val app = store.get(APP_KEY, PainteraTestApplication::class.java)
        app?.close()
        store.remove(APP_KEY)
    }
}