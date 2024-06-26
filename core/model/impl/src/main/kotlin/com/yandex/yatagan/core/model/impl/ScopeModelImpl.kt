package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.BuiltinAnnotation

@JvmInline
internal value class ScopeModelImpl private constructor(
    private val impl: Annotation,
) : ScopeModel {
    init {
        assert(impl.isScope())
    }

    override val customAnnotationClass: AnnotationDeclaration
        get() = impl.annotationClass

    override fun toString() = impl.toString()

    companion object {
        operator fun invoke(annotation: Annotation): ScopeModel =
            annotation.asBuiltin(BuiltinAnnotation.Reusable)?.let {
                return ScopeModel.Reusable
            } ?: ScopeModelImpl(annotation)
    }
}
