package com.yandex.daggerlite.core.lang

sealed interface MemberLangModel : AnnotatedLangModel {
    /**
     * Whether the member is truly static (@[JvmStatic] or `static`).
     */
    val isStatic: Boolean

    /**
     * Member name.
     */
    val name: String
}