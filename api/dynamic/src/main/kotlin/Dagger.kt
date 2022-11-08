package com.yandex.yatagan

import com.yandex.yatagan.Dagger.builder
import com.yandex.yatagan.Dagger.create
import com.yandex.yatagan.base.loadServices
import com.yandex.yatagan.common.loadImplementationByBuilderClass
import com.yandex.yatagan.common.loadImplementationByComponentClass
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.model.impl.ComponentFactoryModel
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.dynamic.RuntimeComponent
import com.yandex.yatagan.dynamic.RuntimeFactory
import com.yandex.yatagan.dynamic.awaitOnError
import com.yandex.yatagan.dynamic.dlLog
import com.yandex.yatagan.lang.InternalLangApi
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.rt.RtModelFactoryImpl
import com.yandex.yatagan.lang.rt.TypeDeclaration
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.format.format
import com.yandex.yatagan.validation.impl.GraphValidationExtension
import com.yandex.yatagan.validation.impl.validate
import com.yandex.yatagan.validation.spi.ValidationPluginProvider
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

/**
 * Dagger Lite entry-point object. Create instances of DL components using reflection.
 *
 * Use either [builder] or [create].
 */
object Dagger {
    @Volatile
    private var validationDelegate: DynamicValidationDelegate? = null
    @Volatile
    private var maxIssueEncounterPaths: Int = 5

    init {
        @OptIn(InternalLangApi::class)
        LangModelFactory.delegate = RtModelFactoryImpl(javaClass.classLoader)
    }

    /**
     * Use this to create a component builder instance for root components that declare it.
     *
     * @param builderClass component builder class
     * @return ready component builder instance of the given class
     *
     * @see Component.Builder
     */
    @JvmStatic
    fun <T : Any> builder(builderClass: Class<T>): T {
        val builder: T
        val time = measureTimeMillis {
            require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
                "$builderClass is not a component builder"
            }

            try {
                return loadImplementationByBuilderClass(builderClass).also {
                    dlLog("Found generated implementation for `$builderClass`, using it")
                }
            } catch (_: ClassNotFoundException) {
                // Fallback to full reflection
            }

            val graph = BindingGraph(
                root = ComponentFactoryModel(TypeDeclaration(builderClass)).createdComponent
            )
            val promise = doValidate(graph)
            val factory = promise.awaitOnError {
                RuntimeFactory(
                    graph = graph,
                    parent = null,
                    validationPromise = promise,
                )
            }
            val proxy = Proxy.newProxyInstance(builderClass.classLoader, arrayOf(builderClass), factory)
            builder = builderClass.cast(proxy)
        }
        dlLog("Dynamic builder creation for `$builderClass` took $time ms")
        return builder
    }

    /**
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    fun <T : Any> create(componentClass: Class<T>): T {
        val componentInstance: T
        val time = measureTimeMillis {
            require(componentClass.isAnnotationPresent(Component::class.java)) {
                "$componentClass is not a component"
            }

            try {
                return loadImplementationByComponentClass(componentClass).also {
                    dlLog("Found generated implementation for `$componentClass`, using it")
                }
            } catch (_: ClassNotFoundException) {
                // Fallback to full reflection
            }

            val graph = BindingGraph(ComponentModel(TypeDeclaration(componentClass)))
            val promise = doValidate(graph)
            val component = promise.awaitOnError {
                RuntimeComponent(
                    graph = graph,
                    parent = null,
                    givenDependencies = emptyMap(),
                    givenInstances = emptyMap(),
                    givenModuleInstances = emptyMap(),
                    validationPromise = promise,
                )
            }
            require(graph.creator == null) {
                "$componentClass has explicit creator interface declared, use `Dagger.builder()` instead"
            }

            val proxy = Proxy.newProxyInstance(componentClass.classLoader, arrayOf(componentClass), component)
            component.thisProxy = proxy
            componentInstance = componentClass.cast(proxy)
        }
        dlLog("Dynamic component creation for `$componentClass` took $time ms")
        return componentInstance
    }

    /**
     * Sets [DynamicValidationDelegate] to be used with all following DL graph creations.
     */
    @JvmStatic
    fun setDynamicValidationDelegate(delegate: DynamicValidationDelegate?) {
        validationDelegate = delegate
    }

    /**
     * Sets max issue encounter path count. No more than [value] paths will be reported for the single message.
     */
    @JvmStatic
    fun setMaxIssueEncounterPaths(value: Int) {
        maxIssueEncounterPaths = value
    }

    private val pluginProviders by lazy {
        loadServices<ValidationPluginProvider>()
    }

    private fun reportMessages(
        messages: Collection<LocatedMessage>,
        reporting: DynamicValidationDelegate.ReportingDelegate,
    ) {
        messages.forEach { locatedMessage ->
            val text: RichString = locatedMessage.format(
                maxEncounterPaths = maxIssueEncounterPaths,
            )
            when (locatedMessage.message.kind) {
                ValidationMessage.Kind.Error -> reporting.reportError(text)
                ValidationMessage.Kind.Warning -> reporting.reportWarning(text)
                ValidationMessage.Kind.MandatoryWarning -> reporting.reportMandatoryWarning(text)
            }
        }
    }

    private fun doValidate(graph: BindingGraph) = validationDelegate?.let { delegate ->
        delegate.dispatchValidation(title = graph.model.type.toString()) { reporting ->
            reportMessages(messages = validate(graph), reporting = reporting)
            if (delegate.usePlugins) {
                val extension = GraphValidationExtension(
                    validationPluginProviders = pluginProviders,
                    graph = graph,
                )
                reportMessages(messages = validate(extension), reporting = reporting)
            }
        }
    }
}