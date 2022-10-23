package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtAnnotated
import com.yandex.daggerlite.lang.compiled.CtParameterBase
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class JavaxParameterImpl(
    private val impl: VariableElement,
    refinedType: TypeMirror,
) : CtParameterBase(), CtAnnotated by JavaxAnnotatedImpl(impl) {
    override val name: String get() = impl.simpleName.toString()
    override val type: Type by lazy(PUBLICATION) { JavaxTypeImpl(refinedType) }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxParameterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}