/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.ErrorType
import com.yandex.yatagan.lang.common.NoDeclaration
import com.yandex.yatagan.lang.common.TypeBase
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

internal class RtTypeImpl private constructor(
    lexicalScope: LexicalScope,
    wrapper: TypeEquivalenceWrapper,
) : TypeBase(), LexicalScope by lexicalScope {
    val impl: ReflectType = wrapper.type

    override val declaration: TypeDeclaration by lazy {
        if (impl.tryAsClass() != null) RtTypeDeclarationImpl(this) else NoDeclaration(this)
    }

    override val typeArguments: List<Type> by lazy {
        when (impl) {
            is ParameterizedType -> impl.actualTypeArguments.map { type ->
                Factory(when(type) {
                    is WildcardType -> {
                        type.lowerBounds.singleOrNull() ?: type.upperBounds.singleOrNull() ?: Any::class.java
                    }
                    else -> type
                })
            }
            else -> emptyList()
        }
    }

    override val isVoid: Boolean
        get() = impl.tryAsClass() == Void.TYPE

    override val isUnresolved: Boolean
        get() = false

    override fun asBoxed(): Type {
        return Factory(when(impl) {
            is Class<*> -> impl.boxed()
            else -> impl
        })
    }

    override fun isAssignableFrom(another: Type): Boolean {
        return when (another) {
            is RtTypeImpl -> impl.isAssignableFrom(another.impl)
            else -> false
        }
    }

    override fun toString(): String = impl.formatString()

    companion object Factory : FactoryKey<ReflectType, Type> {
        private object Caching : FactoryKey<TypeEquivalenceWrapper, RtTypeImpl> {
            override fun LexicalScope.factory() = caching(::RtTypeImpl)
        }

        override fun LexicalScope.factory() = fun LexicalScope.(type: ReflectType): Type {
            return if (type is TypeVariable<*>) {
                ErrorType(nameHint = "<unresolved-type-var: ${type.name}>")
            } else {
                Caching(TypeEquivalenceWrapper(type))
            }
        }
    }
}
