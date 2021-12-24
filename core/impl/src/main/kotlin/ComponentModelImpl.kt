package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ComponentModel.EntryPoint
import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.buildError
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ComponentModel, ConditionalHoldingModelImpl(declaration.conditionals) {

    private val impl = declaration.componentAnnotationIfPresent

    override val type: TypeLangModel
        get() = declaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = declaration.asType(),
        forQualifier = null,
    )

    override val scope = declaration.annotations.find(AnnotationLangModel::isScope)

    override val modules: Set<ModuleModel> by lazy(NONE) {
        val seenModules = hashSetOf<ModuleModel>()
        val moduleQueue: ArrayDeque<ModuleModel> = ArrayDeque(
            impl?.modules?.map(TypeLangModel::declaration)?.map { ModuleModelImpl(it) }?.toList() ?: emptyList())
        while (moduleQueue.isNotEmpty()) {
            val module = moduleQueue.removeFirst()
            if (!seenModules.add(module)) {
                continue
            }
            moduleQueue += module.includes
        }
        seenModules
    }

    override val dependencies: Set<ComponentDependencyModel> by lazy(NONE) {
        impl?.dependencies?.map { ComponentDependencyModelImpl(it) }?.toSet() ?: emptySet()
    }

    override val entryPoints: Set<EntryPoint> by lazy(NONE) {
        class EntryPointImpl(
            override val getter: FunctionLangModel,
            override val dependency: NodeDependency,
        ) : EntryPoint {
            override fun validate(validator: Validator) {
                validator.child(dependency.node)
            }
        }

        declaration.allPublicFunctions.filter {
            it.isAbstract && it.parameters.none()
        }.map { function ->
            EntryPointImpl(
                dependency = NodeDependency(
                    type = function.returnType,
                    forQualifier = function,
                ),
                getter = function,
            )
        }.toSet()
    }

    override val memberInjectors: Set<MembersInjectorModel> by lazy(NONE) {
        declaration.allPublicFunctions.filter {
            MembersInjectorModelImpl.canRepresent(it)
        }.map { function ->
            MembersInjectorModelImpl(
                injector = function,
            )
        }.toSet()
    }

    override val factory: ComponentFactoryModel? by lazy(NONE) {
        declaration.nestedInterfaces
            .find { ComponentFactoryModelImpl.canRepresent(it) }?.let {
                ComponentFactoryModelImpl(factoryDeclaration = it, createdComponent = this)
            }
    }

    override val isRoot: Boolean = impl?.isRoot ?: false

    override val variant: Variant by lazy(NONE) {
        VariantImpl(impl?.variant ?: emptySequence())
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        for (module in modules) {
            validator.child(module)
        }
        for (dependency in dependencies) {
            validator.child(dependency)
        }
        for (entryPoint in entryPoints) {
            validator.child(entryPoint)
        }
        for (memberInjector in memberInjectors) {
            validator.child(memberInjector)
        }

        factory?.let(validator::child)

        if (impl == null) {
            validator.report(buildError {
                contents = "$declaration is not annotated with @Component"
            })
        }

        if (!declaration.isInterface) {
            validator.report(buildError {
                contents = "Component declaration must be an interface"
            })
        }

        if (factory == null) {
            if (!isRoot) {
                validator.report(buildError {
                    contents = "Non-root component declaration must include factory declaration"
                })
            }

            if (dependencies.isNotEmpty()) {
                validator.report(buildError {
                    contents = "Component declares dependencies, yet no factory declaration is present"
                })
            }

            if (modules.any { it.requiresInstance && !it.isTriviallyConstructable }) {
                validator.report(buildError {
                    contents = "Component includes non-trivially constructable modules, that require instance, " +
                            "yet no factory declaration is present"
                })
            }
        }
    }

    override fun toString() = buildString {
        val declarationString = declaration.toString()
        append(declarationString)
        if (!declarationString.endsWith("Component")) {
            append(" (Component)")
        }
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ComponentModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ComponentModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.componentAnnotationIfPresent != null
        }
    }
}
