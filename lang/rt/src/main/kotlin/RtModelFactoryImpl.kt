package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.TypeDeclarationLangModel

class RtModelFactoryImpl(
    private val classLoader: ClassLoader,
) : LangModelFactory {
    private val listClass = classLoader.loadClass("java.util.List")
    private val setClass = classLoader.loadClass("java.util.Set")
    private val mapClass = classLoader.loadClass("java.util.Map")
    private val collectionClass = classLoader.loadClass("java.util.Collection")
    private val providerClass = classLoader.loadClass("javax.inject.Provider")

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        parameter as RtTypeImpl
        val clazz = when (type) {
            LangModelFactory.ParameterizedType.List -> listClass
            LangModelFactory.ParameterizedType.Set -> setClass
            LangModelFactory.ParameterizedType.Collection -> collectionClass
            LangModelFactory.ParameterizedType.Provider -> providerClass
        }
        val arg = if (isCovariant) WildcardTypeImpl(upperBound = parameter.impl) else parameter.impl
        return RtTypeImpl(ParameterizedTypeImpl(arg, raw = clazz))
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        valueType as RtTypeImpl
        val valueArg = if (isCovariant) WildcardTypeImpl(upperBound = valueType.impl) else valueType.impl
        return RtTypeImpl(ParameterizedTypeImpl((keyType as RtTypeImpl).impl, valueArg, raw = mapClass))
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclarationLangModel? {
        val qualifiedName = buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            append(simpleName)
            for (name in simpleNames) {
                append('$').append(name)
            }
        }
        return try {
            RtTypeDeclarationImpl(RtTypeImpl(classLoader.loadClass(qualifiedName)))
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    override val errorType: Type
        get() = RtTypeImpl(ErrorType())

    override val isInRuntimeEnvironment: Boolean
        get() = true

    private class ErrorType : ReflectType {
        override fun toString() = "<error>"
    }

    private class WildcardTypeImpl(
        private val upperBound: ReflectType,
    ) : ReflectWildcardType {
        private val upperBounds = arrayOf(upperBound)
        override fun getUpperBounds() = upperBounds
        override fun getLowerBounds() = emptyArray<ReflectType>()
        override fun toString() = "? extends $upperBound"
    }

    private class ParameterizedTypeImpl(
        private vararg val arguments: ReflectType,
        private val raw: ReflectType,
    ) : ReflectParameterizedType {
        override fun getActualTypeArguments() = arguments
        override fun getRawType() = raw
        override fun getOwnerType() = null
        override fun toString() = buildString {
            append(raw)
            arguments.joinTo(this, prefix = "<", postfix = ">")
        }
    }
}
