package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.common.TypeBase

/**
 * [Type] base class, that can be named by [CtTypeNameModel].
 */
abstract class CtType : TypeBase() {
    /**
     * Class name.
     * @see CtTypeNameModel
     */
    abstract val nameModel: CtTypeNameModel

    final override fun toString() = nameModel.toString()
}