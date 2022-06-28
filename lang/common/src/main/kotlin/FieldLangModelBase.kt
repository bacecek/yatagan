package com.yandex.daggerlite.lang.common

import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel

abstract class FieldLangModelBase : FieldLangModel {
    final override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitField(this)
    }

    final override fun toString() = "$owner::$name: $type"
}