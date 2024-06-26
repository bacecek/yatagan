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

package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.Annotation.Value
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.compiled.CtAnnotationDeclarationBase
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.AbstractAnnotationValueVisitor8
import javax.lang.model.util.ElementFilter

internal class JavaxAnnotationImpl private constructor(
    lexicalScope: LexicalScope,
    wrapped: AnnotationMirrorEquivalence,
) : CtAnnotationBase(), LexicalScope by lexicalScope {
    private val impl: AnnotationMirror = wrapped.get()

    override val annotationClass: AnnotationDeclaration by lazy {
        AnnotationClassImpl(impl.annotationType.asTypeElement())
    }

    override val platformModel: AnnotationMirror
        get() = impl

    override fun getValue(attribute: AnnotationDeclaration.Attribute): Value {
        require(attribute is AttributeImpl) { "Invalid attribute type" }
        val value = impl.elementValues[attribute.impl] ?: attribute.impl.defaultValue
        checkNotNull(value) { "Attribute missing/invalid" }
        return ValueImpl(
            value = value,
            type = attribute.impl.returnType,
        )
    }

    companion object Factory : FactoryKey<AnnotationMirror, JavaxAnnotationImpl> {
        private object Caching : FactoryKey<AnnotationMirrorEquivalence, JavaxAnnotationImpl> {
            override fun LexicalScope.factory() = caching(::JavaxAnnotationImpl)
        }

        override fun LexicalScope.factory() = fun LexicalScope.(it: AnnotationMirror): JavaxAnnotationImpl {
            return Caching(AnnotationMirrorEquivalence(it))
        }
    }

    private inner class ValueImpl(
        private val value: AnnotationValue,
        private val type: TypeMirror,
    ) : ValueBase() {
        private val equivalence = AnnotationValueEquivalence(value)

        override val platformModel: AnnotationValue
            get() = value

        override fun <R> accept(visitor: Value.Visitor<R>): R {
            return value.accept(object : AbstractAnnotationValueVisitor8<R, Unit>() {
                override fun visitBoolean(b: Boolean, p: Unit?): R = visitor.visitBoolean(b)
                override fun visitByte(b: Byte, p: Unit?): R = visitor.visitByte(b)
                override fun visitChar(c: Char, p: Unit?): R = visitor.visitChar(c)
                override fun visitDouble(d: Double, p: Unit?): R = visitor.visitDouble(d)
                override fun visitFloat(f: Float, p: Unit?): R = visitor.visitFloat(f)
                override fun visitInt(i: Int, p: Unit?): R = visitor.visitInt(i)
                override fun visitLong(i: Long, p: Unit?): R = visitor.visitLong(i)
                override fun visitShort(s: Short, p: Unit?): R = visitor.visitShort(s)
                override fun visitString(s: String, p: Unit?): R {
                    return if (s == "<error>" &&
                        (type.kind != TypeKind.DECLARED || type.asTypeElement() != Utils.stringType)) {
                        // If the type is not string, yet the value is the "<error>" string, then it actually
                        //  represents an unresolved type.
                        visitor.visitType(JavaxTypeImpl(Utils.types.nullType))
                    } else {
                        visitor.visitString(s)
                    }
                }
                override fun visitType(t: TypeMirror, p: Unit?): R = visitor.visitType(JavaxTypeImpl(t))
                override fun visitAnnotation(a: AnnotationMirror, p: Unit?): R = visitor.visitAnnotation(Factory(a))
                override fun visitEnumConstant(c: VariableElement, p: Unit?): R {
                    val enum = JavaxTypeImpl(c.enclosingElement.asTypeElement().asType().asDeclaredType())
                    return visitor.visitEnumConstant(enum, c.simpleName.toString())
                }
                override fun visitArray(vals: List<AnnotationValue>, p: Unit?): R {
                    val arrayType = type.asArrayType()
                    return visitor.visitArray(vals.map {
                        ValueImpl(value = it, type = arrayType.componentType)
                    })
                }
                override fun visitUnknown(av: AnnotationValue?, p: Unit?): R = visitor.visitUnresolved()
            }, Unit)
        }

        override fun hashCode() = equivalence.hashCode()
        override fun equals(other: Any?) = this === other || (other is ValueImpl && equivalence == other.equivalence)
    }

    private class AttributeImpl private constructor(
        lexicalScope: LexicalScope,
        val impl: ExecutableElement,
    ) : AnnotationDeclaration.Attribute, LexicalScope by lexicalScope {
        override val name: String
            get() = impl.simpleName.toString()
        override val type: Type
            get() = JavaxTypeImpl(impl.returnType)

        companion object Factory : FactoryKey<ExecutableElement, AttributeImpl> {
            override fun LexicalScope.factory() = ::AttributeImpl
        }
    }

    private class AnnotationClassImpl private constructor(
        lexicalScope: LexicalScope,
        private val impl: TypeElement,
    ) : CtAnnotationDeclarationBase(), CtAnnotated by JavaxAnnotatedImpl(lexicalScope, impl), LexicalScope by lexicalScope {

        override val attributes: Sequence<AnnotationDeclaration.Attribute> by lazy {
            ElementFilter.methodsIn(impl.enclosedElements)
                .asSequence()
                .filter { it.isAbstract }
                .map {
                    AttributeImpl(it)
                }
                .memoize()
        }

        override val qualifiedName: String
            get() = impl.qualifiedName.toString()

        override fun getRetention(): AnnotationRetention {
            // Kapt generates java retention counterparts, so no need to be kotlin-aware here
            val retention = impl.annotationMirrors.find {
                it.annotationType.asTypeElement().qualifiedName.contentEquals("java.lang.annotation.Retention")
            }
            return retention?.elementValues?.values?.firstOrNull()?.accept(
                object : AbstractAnnotationValueVisitor8Adapter<AnnotationRetention?>() {
                    override fun visitDefault(): AnnotationRetention? = null
                    override fun visitEnumConstant(c: VariableElement, p: Nothing?): AnnotationRetention? {
                        return when (c.simpleName.toString()) {
                            "SOURCE" -> AnnotationRetention.SOURCE
                            "CLASS" -> AnnotationRetention.BINARY
                            "RUNTIME" -> AnnotationRetention.RUNTIME
                            else -> null
                        }
                    }
                }, null
            ) ?: AnnotationRetention.BINARY  // Default java retention
        }

        companion object Factory : FactoryKey<TypeElement, AnnotationClassImpl> {
            override fun LexicalScope.factory() = caching(::AnnotationClassImpl)
        }
    }
}
