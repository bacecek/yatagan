/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import org.logicng.formulas.Formula

internal interface BooleanExpressionInternal : BooleanExpression, LexicalScope {
    val formula: Formula

    fun <R> accept(visitor: Visitor<R>): R

    fun negate(): BooleanExpressionInternal {
        return NotExpressionImpl(this)
    }

    interface Not : BooleanExpression.Not, BooleanExpressionInternal
    interface And : BooleanExpression.And, BooleanExpressionInternal {
        override val lhs: BooleanExpressionInternal
        override val rhs: BooleanExpressionInternal
    }
    interface Or : BooleanExpression.Or, BooleanExpressionInternal {
        override val lhs: BooleanExpressionInternal
        override val rhs: BooleanExpressionInternal
    }
    interface Variable : BooleanExpression.Variable, BooleanExpressionInternal

    interface Visitor<R> {
        fun visitVariable(variable: Variable): R
        fun visitNot(not: Not): R
        fun visitAnd(and: And): R
        fun visitOr(or: Or): R
    }
}

internal class NotExpressionImpl private constructor(
    override val underlying: BooleanExpressionInternal,
) : BooleanExpressionInternal.Not, LexicalScope by underlying {
    override val formula: Formula
        // factory caches things itself, so no need to cache here
        get() = underlying.formula.negate()

    override fun negate() = underlying

    override fun <R> accept(visitor: BooleanExpression.Visitor<R>) = visitor.visitNot(this)
    override fun <R> accept(visitor: BooleanExpressionInternal.Visitor<R>) = visitor.visitNot(this)

    companion object Factory : FactoryKey<BooleanExpressionInternal, NotExpressionImpl> {
        override fun LexicalScope.factory() = caching(::NotExpressionImpl)
    }
}

internal class AndExpressionImpl private constructor(
    operands: Pair<BooleanExpressionInternal, BooleanExpressionInternal>,
) : BooleanExpressionInternal.And, LexicalScope by operands.first {
    override val lhs: BooleanExpressionInternal = operands.first
    override val rhs: BooleanExpressionInternal = operands.second
    override val formula: Formula by lazy {
        val conjuncts = ArrayList<BooleanExpressionInternal>(4)
        // (...((a && b) && c) && d) && ...) => a && b && c && d && ...
        val flattenAnds = object : BooleanExpressionInternal.Visitor<Unit> {
            override fun visitVariable(variable: BooleanExpressionInternal.Variable) = conjuncts.plusAssign(variable)
            override fun visitNot(not: BooleanExpressionInternal.Not) = conjuncts.plusAssign(not)
            override fun visitOr(or: BooleanExpressionInternal.Or) = conjuncts.plusAssign(or)
            override fun visitAnd(and: BooleanExpressionInternal.And) = and.lhs.accept(this).also { and.rhs.accept(this) }
        }
        lhs.accept(flattenAnds)
        rhs.accept(flattenAnds)
        LogicNg.formulaFactory.and(conjuncts.map(BooleanExpressionInternal::formula))
    }

    override fun <R> accept(visitor: BooleanExpression.Visitor<R>) = visitor.visitAnd(this)
    override fun <R> accept(visitor: BooleanExpressionInternal.Visitor<R>) = visitor.visitAnd(this)

    companion object Factory : FactoryKey<Pair<BooleanExpressionInternal, BooleanExpressionInternal>, AndExpressionImpl> {
        override fun LexicalScope.factory() = caching(::AndExpressionImpl)
    }
}

internal class OrExpressionImpl private constructor(
    operands: Pair<BooleanExpressionInternal, BooleanExpressionInternal>,
) : BooleanExpressionInternal.Or, LexicalScope by operands.first {
    override val lhs: BooleanExpressionInternal = operands.first
    override val rhs: BooleanExpressionInternal = operands.second

    override val formula: Formula by lazy {
        val disjuncts = ArrayList<BooleanExpressionInternal>(4)
        // (...((a || b) || c) || d) || ...) => a || b || c || d || ...
        val flattenOrs = object : BooleanExpressionInternal.Visitor<Unit> {
            override fun visitVariable(variable: BooleanExpressionInternal.Variable) = disjuncts.plusAssign(variable)
            override fun visitNot(not: BooleanExpressionInternal.Not) = disjuncts.plusAssign(not)
            override fun visitAnd(and: BooleanExpressionInternal.And) = disjuncts.plusAssign(and)
            override fun visitOr(or: BooleanExpressionInternal.Or) = or.lhs.accept(this).also { or.rhs.accept(this) }
        }
        lhs.accept(flattenOrs)
        rhs.accept(flattenOrs)
        LogicNg.formulaFactory.or(disjuncts.map(BooleanExpressionInternal::formula))
    }

    override fun <R> accept(visitor: BooleanExpression.Visitor<R>) = visitor.visitOr(this)
    override fun <R> accept(visitor: BooleanExpressionInternal.Visitor<R>) = visitor.visitOr(this)

    companion object Factory : FactoryKey<Pair<BooleanExpressionInternal, BooleanExpressionInternal>, OrExpressionImpl> {
        override fun LexicalScope.factory() = caching(::OrExpressionImpl)
    }
}

internal abstract class VariableBaseImpl : ConditionModel, BooleanExpressionInternal.Variable {
    protected abstract val pathSource: String

    final override val model: ConditionModel
        get() = this

    final override val formula: Formula by lazy {
        LogicNg.formulaFactory.variable("${root.type}::${pathSource}")
    }

    final override fun <R> accept(visitor: BooleanExpression.Visitor<R>) = visitor.visitVariable(this)
    final override fun <R> accept(visitor: BooleanExpressionInternal.Visitor<R>) = visitor.visitVariable(this)
}
