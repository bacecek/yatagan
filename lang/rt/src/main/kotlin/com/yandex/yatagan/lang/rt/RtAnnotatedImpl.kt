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

import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.scope.LexicalScope

internal class RtAnnotatedImpl(
    lexicalScope: LexicalScope,
    private val impl: ReflectAnnotatedElement,
) : Annotated {
    override val annotations: Sequence<Annotation> by lazy {
        impl.declaredAnnotations.map { with(lexicalScope) { RtAnnotationImpl(it) } }.asSequence()
    }

    override fun <A : kotlin.Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}