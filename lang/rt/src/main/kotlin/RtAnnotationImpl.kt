package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Annotation.Value
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.AnnotationBase
import com.yandex.yatagan.lang.common.AnnotationDeclarationBase
import javax.inject.Qualifier
import javax.inject.Scope

internal class RtAnnotationImpl(
    private val impl: Annotation,
) : AnnotationBase() {

    override val annotationClass: AnnotationDeclaration
        get() = AnnotationClassImpl(impl.javaAnnotationClass)

    override val platformModel: Annotation
        get() = impl

    override fun getValue(attribute: AnnotationDeclaration.Attribute): Value {
        require(attribute is AttributeImpl) { "Invalid attribute type" }
        return try {
            ValueImpl(attribute.impl.invoke(impl))
        } catch (e: ReflectiveOperationException) {
            ValueImpl(null)
        }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtAnnotationImpl && impl == other.impl)
    }

    override fun hashCode(): Int = impl.hashCode()

    private class ValueImpl(
        private val value: Any?,
    ) : ValueBase() {
        override val platformModel: Any?
            get() = value

        override fun <R> accept(visitor: Value.Visitor<R>): R {
            return when(value) {
                is Boolean -> visitor.visitBoolean(value)
                is Byte -> visitor.visitByte(value)
                is Short -> visitor.visitShort(value)
                is Int -> visitor.visitInt(value)
                is Long -> visitor.visitLong(value)
                is Char -> visitor.visitChar(value)
                is Float -> visitor.visitFloat(value)
                is Double -> visitor.visitDouble(value)
                is ByteArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is ShortArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is IntArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is LongArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is CharArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is FloatArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is DoubleArray -> visitor.visitArray(value.map { ValueImpl(it) })
                is Array<*> -> visitor.visitArray(value.map { ValueImpl(it) })
                is String -> visitor.visitString(value)
                is Class<*> -> visitor.visitType(RtTypeImpl(value))
                is Annotation -> visitor.visitAnnotation(RtAnnotationImpl(value))
                is Enum<*> -> visitor.visitEnumConstant(
                    // Suppress before https://youtrack.jetbrains.com/issue/KT-54005 gets rolled out.
                    enum = @Suppress("ENUM_DECLARING_CLASS_DEPRECATED_WARNING") RtTypeImpl(value.declaringClass),
                    constant = value.name,
                )
                null -> visitor.visitUnresolved()  // Should not be normally reachable
                else -> throw AssertionError("Unexpected value type: $value")
            }
        }

        override fun hashCode() = value.hashCode()
        override fun equals(other: Any?) = this === other || (other is ValueImpl && value == other.value)
    }

    private class AnnotationClassImpl private constructor(
        private val impl: Class<*>,
    ) : AnnotationDeclarationBase(), Annotated by RtAnnotatedImpl(impl) {

        override val attributes: Sequence<AnnotationDeclaration.Attribute> by lazy {
            impl.declaredMethods.asSequence()
                .filter { it.isAbstract }
                .map {
                    AttributeImpl(impl = it)
                }
        }

        override fun isClass(clazz: Class<out Annotation>): Boolean {
            return impl == clazz
        }

        override val qualifiedName: String
            get() = impl.canonicalName

        override fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
            builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>
        ): T? {
            val annotation: BuiltinAnnotation.OnAnnotationClass? = when(builtinAnnotation) {
                BuiltinAnnotation.IntoMap.Key -> (builtinAnnotation as BuiltinAnnotation.IntoMap.Key)
                    .takeIf { impl.isAnnotationPresent(IntoMap.Key::class.java) }
                BuiltinAnnotation.Qualifier -> (builtinAnnotation as BuiltinAnnotation.Qualifier)
                    .takeIf { impl.isAnnotationPresent(Qualifier::class.java) }
                BuiltinAnnotation.Scope -> (builtinAnnotation as BuiltinAnnotation.Scope)
                    .takeIf { impl.isAnnotationPresent(Scope::class.java) }
            }

            return builtinAnnotation.modelClass.cast(annotation)
        }

        override fun getRetention(): AnnotationRetention = AnnotationRetention.RUNTIME

        companion object Factory : ObjectCache<Class<*>, AnnotationClassImpl>() {
            operator fun invoke(clazz: Class<*>) = createCached(clazz) { AnnotationClassImpl(clazz) }
        }
    }

    private class AttributeImpl(
        val impl: ReflectMethod,
    ) : AnnotationDeclaration.Attribute {
        override val name: String
            get() = impl.name

        override val type: Type
            get() = RtTypeImpl(impl.genericReturnType)
    }
}
