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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * This is required to correctly distinguish between Java's primitive and wrapper types, as they all are
 * represented uniformly in Kotlin.
 */
internal class JvmMethodSignature private constructor(
    lexicalScope: LexicalScope,
    declaration: KSFunctionDeclaration,
) : LexicalScope by lexicalScope {
    private val match by lazy {
        Utils.resolver.mapToJvmSignature(declaration)?.let { descriptor ->
            checkNotNull(MethodSignatureRegex.matchEntire(descriptor)) {
                "Not reached: invalid jvm method signature: $descriptor"
            }
        }
    }

    /**
     * Parsed method return type signature. `null` if parsing failed.
     */
    val returnTypeSignature: String? by lazy(PUBLICATION) {
        match?.groupValues?.get(2)
    }

    /**
     * Parsed method parameters' types' signatures. `null` if parsing failed.
     */
    val parameterTypesSignatures: List<String>? by lazy(PUBLICATION) {
        match?.groupValues?.get(1)?.let { params ->
            ParamSignatureRegex.findAll(params).map(MatchResult::value).toList()
        }
    }

    companion object Factory : FactoryKey<KSFunctionDeclaration, JvmMethodSignature> {
        override fun LexicalScope.factory() = ::JvmMethodSignature
    }
}

private val MethodSignatureRegex = """^\((.*)\)(.*)$""".toRegex()
private val ParamSignatureRegex = """\[*(?:B|C|D|F|I|J|S|Z|L.*?;)""".toRegex()

/**
 * This is required for correctly distinguish between Java's primitive and wrapper types, as they all are
 * represented uniformly in Kotlin.
 */
internal sealed interface JvmTypeInfo {
    // Primitive
    object Byte : JvmTypeInfo
    object Char : JvmTypeInfo
    object Double : JvmTypeInfo
    object Float : JvmTypeInfo
    object Int : JvmTypeInfo
    object Long : JvmTypeInfo
    object Short : JvmTypeInfo
    object Boolean : JvmTypeInfo

    // Void
    object Void : JvmTypeInfo

    // Declared, relies on mapped KSType
    object Declared : JvmTypeInfo

    // Array
    data class Array(val elementInfo: JvmTypeInfo) : JvmTypeInfo
}

/**
 * Computes [JvmTypeInfo] from the supplied information.
 * [jvmSignature] and [type] can not be `null` simultaneously.
 *
 * @param jvmSignature optional char sequence encoding JVM type descriptor.
 * @param type an optional explicit type information, corresponding to JVM descriptor, if provided. Must not be `null`
 * if [jvmSignature] is `null`.
 */
internal fun JvmTypeInfo(jvmSignature: CharSequence?, type: KSType?): JvmTypeInfo =
    when (jvmSignature?.first()) {
        'B' -> JvmTypeInfo.Byte
        'C' -> JvmTypeInfo.Char
        'D' -> JvmTypeInfo.Double
        'F' -> JvmTypeInfo.Float
        'I' -> JvmTypeInfo.Int
        'J' -> JvmTypeInfo.Long
        'S' -> JvmTypeInfo.Short
        'Z' -> JvmTypeInfo.Boolean
        '[' -> JvmTypeInfo.Array(elementInfo = JvmTypeInfo(
            jvmSignature = jvmSignature.subSequence(startIndex = 1, endIndex = jvmSignature.length),
            type = type?.arguments?.firstOrNull()?.type?.resolve(),
        ))
        'V' -> JvmTypeInfo.Void
        null -> JvmTypeInfo(checkNotNull(type) { "Not reached: both signature and type can not be null" })
        else -> JvmTypeInfo.Declared
    }

/**
 * Infers [JvmTypeInfo] from the supplied [type].
 *
 * @param type a type to infer [JvmTypeInfo] from.
 */
internal fun JvmTypeInfo(type: KSType): JvmTypeInfo {
    val declaration = type.declaration as? KSClassDeclaration
    return when (declaration?.qualifiedName?.asString()) {
        "kotlin.ByteArray" -> JvmTypeInfo.Array(JvmTypeInfo.Byte)
        "kotlin.IntArray" -> JvmTypeInfo.Array(JvmTypeInfo.Int)
        "kotlin.LongArray" -> JvmTypeInfo.Array(JvmTypeInfo.Long)
        "kotlin.ShortArray" -> JvmTypeInfo.Array(JvmTypeInfo.Short)
        "kotlin.FloatArray" -> JvmTypeInfo.Array(JvmTypeInfo.Float)
        "kotlin.DoubleArray" -> JvmTypeInfo.Array(JvmTypeInfo.Double)
        "kotlin.CharArray" -> JvmTypeInfo.Array(JvmTypeInfo.Char)
        "kotlin.BooleanArray" -> JvmTypeInfo.Array(JvmTypeInfo.Boolean)
        "kotlin.Array" -> JvmTypeInfo.Array(JvmTypeInfo.Declared)
        // kotlin.Unit shouldn't be mapped to `void` as it actually can appear in Java code in reference type positions.
        else -> JvmTypeInfo.Declared
    }
}