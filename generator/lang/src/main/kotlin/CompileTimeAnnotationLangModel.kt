package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

interface CompileTimeAnnotationLangModel : AnnotationLangModel {
    /**
     * Returns the [attribute] value as boolean. Must handle default values.
     * If type of the actual attribute is not [Boolean] then the behavior is undefined.
     */
    fun getBoolean(attribute: String): Boolean

    /**
     * Returns the [attribute] value as an array of types. Must handle default values.
     * If type of the actual attribute is not the array of types then the behavior is undefined.
     */
    fun getTypes(attribute: String): Sequence<TypeLangModel>
}