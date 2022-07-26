package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSModifierListOwner
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel
import com.yandex.daggerlite.lang.common.ConstructorLangModelBase
import com.yandex.daggerlite.lang.common.FieldLangModelBase

internal class KspTypeDeclarationImpl private constructor(
    val type: KspTypeImpl,
) : CtTypeDeclarationLangModel() {
    private val impl: KSClassDeclaration = type.impl.declaration as KSClassDeclaration
    private val annotated = KspAnnotatedImpl(impl)

    override val annotations: Sequence<CtAnnotationLangModel> = annotated.annotations
    override fun <A : Annotation> isAnnotatedWith(type: Class<A>) = annotated.isAnnotatedWith(type)

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublicOrInternal()

    override val isInterface: Boolean
        get() = impl.classKind == ClassKind.INTERFACE

    override val isAbstract: Boolean
        get() = impl.isAbstract()

    override val kotlinObjectKind: KotlinObjectKind?
        get() = when {
            impl.isCompanionObject && impl.simpleName.asString() == "Companion" -> KotlinObjectKind.Companion
            impl.classKind == ClassKind.OBJECT -> KotlinObjectKind.Object
            else -> null
        }

    override val qualifiedName: String
        get() = impl.qualifiedName?.asString() ?: ""

    override val enclosingType: TypeDeclarationLangModel?
        get() = (impl.parentDeclaration as? KSClassDeclaration)?.let { Factory(KspTypeImpl(it.asType(emptyList()))) }

    override val implementedInterfaces: Sequence<TypeLangModel> = sequence {
        val queue = ArrayDeque<Sequence<KSTypeReference>>()
        queue += impl.superTypes
        while (queue.isNotEmpty()) {
            for (typeRef in queue.removeFirst()) {
                val declaration = typeRef.resolve().resolveAliasIfNeeded() as? KSClassDeclaration ?: continue
                queue += declaration.superTypes
                if (declaration.classKind == ClassKind.INTERFACE) {
                    yield(KspTypeImpl(typeRef))
                }
            }
        }
    }.memoize()

    override val constructors: Sequence<ConstructorLangModel> by lazy {
        impl.getConstructors()
            .filter { !it.isPrivate() }
            .map { ConstructorImpl(platformModel = it) }
            .memoize()
    }

    private interface FunctionFilter {
        fun filterFunction(func: KSFunctionDeclaration): Boolean = filterAll(func)
        fun filterProperty(prop: KSPropertyDeclaration): Boolean = filterAll(prop)
        fun filterAccessor(accessor: KSPropertyAccessor): Boolean = filterAll(accessor)
        fun filterAll(it: KSAnnotated) = true
    }

    override val functions: Sequence<FunctionLangModel> by lazy {
        sequence {
            when (kotlinObjectKind) {
                KotlinObjectKind.Object -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {},
                        isStatic = { it.isAnnotationPresent<JvmStatic>() }
                    )
                }
                KotlinObjectKind.Companion -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {
                            override fun filterAll(it: KSAnnotated): Boolean {
                                // Skip jvm-static methods in companion
                                return !it.isAnnotationPresent<JvmStatic>()
                            }
                        },
                        isStatic = { false }
                    )
                }
                null -> {
                    functionsImpl(
                        declaration = impl,
                        filter = object : FunctionFilter {},
                        isStatic = { it is KSModifierListOwner && Modifier.JAVA_STATIC in it.modifiers }
                    )
                    impl.getCompanionObject()?.let { companion ->
                        functionsImpl(
                            declaration = companion,
                            filter = object : FunctionFilter {
                                // Include jvm static from companion
                                override fun filterFunction(func: KSFunctionDeclaration): Boolean {
                                    return func.isAnnotationPresent<JvmStatic>()
                                }

                                override fun filterProperty(prop: KSPropertyDeclaration): Boolean {
                                    return true
                                }

                                override fun filterAccessor(accessor: KSPropertyAccessor): Boolean {
                                    return accessor.isAnnotationPresent<JvmStatic>() ||
                                            accessor.receiver.isAnnotationPresent<JvmStatic>()
                                }
                            },
                            isStatic = { true },
                        )
                    }
                }
            }
        }.memoize()
    }

    private suspend fun SequenceScope<FunctionLangModel>.functionsImpl(
        declaration: KSClassDeclaration,
        filter: FunctionFilter,
        isStatic: (KSAnnotated) -> Boolean,
    ) {
        for (function in declaration.allNonPrivateFunctions()) {
            if (!filter.filterFunction(function)) continue
            yield(KspFunctionImpl(
                owner = this@KspTypeDeclarationImpl,
                impl = function,
                isStatic = isStatic(function),
            ))
        }

        for (property in declaration.allNonPrivateProperties()) {
            if (!filter.filterProperty(property)) continue
            explodeProperty(
                property = property,
                owner = this@KspTypeDeclarationImpl,
                filter = filter,
                isStatic = isStatic,
            )
        }
    }

    private suspend fun SequenceScope<FunctionLangModel>.explodeProperty(
        property: KSPropertyDeclaration,
        owner: KspTypeDeclarationImpl,
        filter: FunctionFilter,
        isStatic: (KSAnnotated) -> Boolean,
    ) {
        val isPropertyStatic = isStatic(property)
        property.getter?.let { getter ->
            if (filter.filterAccessor(getter)) {
                yield(KspFunctionPropertyGetterImpl(
                    owner = owner, getter = getter,
                    isStatic = isPropertyStatic || isStatic(getter)
                ))
            }
        }
        property.setter?.let { setter ->
            val modifiers = setter.modifiers
            if (Modifier.PRIVATE !in modifiers && Modifier.PROTECTED !in modifiers && filter.filterAccessor(setter)) {
                yield(KspFunctionPropertySetterImpl(
                    owner = owner, setter = setter,
                    isStatic = isPropertyStatic || isStatic(setter)
                ))
            }
        }
    }

    private suspend fun SequenceScope<FieldLangModel>.yieldInheritedFields() {
        val declared = impl.getDeclaredProperties().toSet()

        // We can't use `getAllProperties()` here, as it doesn't handle Java's "shadowed" fields properly.
        suspend fun SequenceScope<FieldLangModel>.includeFieldsFrom(type: KSType) {
            val clazz = type.declaration as? KSClassDeclaration ?: return
            clazz.getDeclaredProperties()
                .filter {
                    !it.isPrivate() && ((it.getter == null && it.setter == null &&
                            Modifier.JAVA_STATIC !in it.modifiers) /* Java field */ ||
                            it.isLateInit() /* lateinit property always exposes a field */) && it !in declared
                }
                .forEach { property ->
                    yield(
                        KspFieldImpl(
                            impl = property,
                            owner = this@KspTypeDeclarationImpl,
                            refinedOwner = type,
                            isStatic = false,
                        )
                    )
                }
            clazz.getSuperclass()?.let { includeFieldsFrom(it) }
        }
        includeFieldsFrom(type.impl)
    }

    override val fields: Sequence<FieldLangModel> = run {
        sequence {
            when (kotlinObjectKind) {
                KotlinObjectKind.Object -> {
                    for (property in impl.getDeclaredProperties()) {
                        // `lateinit` generates exposed field
                        if (property.isPrivate() || (!property.isKotlinFieldInObject() && !property.isLateInit())) {
                            continue  // Not a field
                        }
                        yield(KspFieldImpl(
                                impl = property,
                                owner = this@KspTypeDeclarationImpl,
                                isStatic = true,  // Every field is static in kotlin object.
                        ))
                    }
                    // Simulate INSTANCE field for static kotlin singleton.
                    yield(PSFSyntheticField(
                            owner = this@KspTypeDeclarationImpl,
                            name = "INSTANCE",
                    ))
                }
                KotlinObjectKind.Companion -> {
                    // Nothing here, no fields are actually generated in companion,
                    //  they are all generated in the enclosing class.
                }
                null -> {
                    when (impl.origin) {
                        Origin.JAVA, Origin.JAVA_LIB -> {
                            // Then any "property" represents a field in Java
                            for (field in impl.getDeclaredProperties()) {
                                if (field.isPrivate()) continue
                                yield(KspFieldImpl(
                                    impl = field,
                                    owner = this@KspTypeDeclarationImpl,
                                    isStatic = Modifier.JAVA_STATIC in field.modifiers,
                                ))
                            }
                        }
                        Origin.KOTLIN, Origin.KOTLIN_LIB, Origin.SYNTHETIC -> {
                            // Assume kotlin origin.
                            for (property in impl.getDeclaredProperties()) {
                                // Only `lateinit` properties expose a field in regular kotlin class
                                if (property.isPrivate() || !property.isLateInit()) {
                                    continue
                                }
                                yield(KspFieldImpl(
                                    impl = property,
                                    owner = this@KspTypeDeclarationImpl,
                                    isStatic = false,
                                ))
                            }
                            impl.getCompanionObject()?.let { companion ->
                                // Include fields from companion (if any) as they are generated as static fields
                                //  in the enclosing class.
                                for (property in companion.getDeclaredProperties()) {
                                    if (property.isPrivate() ||
                                        (!property.isKotlinFieldInObject() && !property.isLateInit())) {
                                        continue
                                    }
                                    yield(KspFieldImpl(
                                        impl = property,
                                        owner = this@KspTypeDeclarationImpl,
                                        isStatic = true,  // Every field is static in companion
                                    ))
                                }
                                // Simulate companion object instance field.
                                yield(PSFSyntheticField(
                                    owner = this@KspTypeDeclarationImpl,
                                    type = KspTypeImpl(companion.asType(emptyList())),
                                    name = companion.simpleName.asString(),
                                ))
                            }
                        }
                    }
                }
            }
            yieldInheritedFields()
        }.memoize()
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy {
        impl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { !it.isPrivate() }
            .map { Factory(KspTypeImpl(it.asType(emptyList()))) }
            .memoize()
    }

    override val defaultCompanionObjectDeclaration: KspTypeDeclarationImpl? by lazy {
        impl.getCompanionObject()?.takeIf {
            it.simpleName.asString() == "Companion"
        }?.let { companion ->
            KspTypeDeclarationImpl(KspTypeImpl(companion.asType(emptyList())))
        }
    }

    override fun asType(): TypeLangModel {
        return type
    }

    override val platformModel: KSClassDeclaration
        get() = impl

    companion object Factory : ObjectCache<KspTypeImpl, KspTypeDeclarationImpl>() {
        operator fun invoke(impl: KspTypeImpl) =
            createCached(impl, ::KspTypeDeclarationImpl)
    }

    private inner class ConstructorImpl(
        override val platformModel: KSFunctionDeclaration,
    ) : ConstructorLangModelBase(), AnnotatedLangModel by KspAnnotatedImpl(platformModel) {
        private val jvmSignature = JvmMethodSignature(platformModel)

        override val isEffectivelyPublic: Boolean
            get() {
                if (platformModel.origin == Origin.SYNTHETIC) {
                    val constructeeOrigin = this@KspTypeDeclarationImpl.platformModel.origin
                    if (constructeeOrigin == Origin.JAVA || constructeeOrigin == Origin.JAVA_LIB) {
                        // Java synthetic constructor has the same visibility as the class.
                        return this@KspTypeDeclarationImpl.isEffectivelyPublic
                    }
                }
                return platformModel.isPublicOrInternal()
            }
        override val constructee: TypeDeclarationLangModel get() = this@KspTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(
            declaration = platformModel,
            containing = type.impl,
            jvmMethodSignature = jvmSignature,
        )
    }

    private class PSFSyntheticField(
        override val owner: TypeDeclarationLangModel,
        override val type: TypeLangModel = owner.asType(),
        override val name: String,
    ) : FieldLangModelBase() {
        override val isEffectivelyPublic: Boolean get() = true
        override val annotations: Sequence<Nothing> get() = emptySequence()
        override val platformModel: Any? get() = null
        override val isStatic: Boolean get() = true
    }
}