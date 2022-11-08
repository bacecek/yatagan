package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.ErrorNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel


internal fun CtTypeNameModel(
    type: KSType,
    jvmTypeKind: JvmTypeInfo = JvmTypeInfo(type),
): CtTypeNameModel {
    return nameModelImpl(type = type, jvmTypeKind = jvmTypeKind)
}

private fun nameModelImpl(
    type: KSType?,
    jvmTypeKind: JvmTypeInfo,
): CtTypeNameModel {
    return when (jvmTypeKind) {
        JvmTypeInfo.Void -> KeywordTypeNameModel.Void
        JvmTypeInfo.Byte -> KeywordTypeNameModel.Byte
        JvmTypeInfo.Char -> KeywordTypeNameModel.Char
        JvmTypeInfo.Double -> KeywordTypeNameModel.Double
        JvmTypeInfo.Float -> KeywordTypeNameModel.Float
        JvmTypeInfo.Int -> KeywordTypeNameModel.Int
        JvmTypeInfo.Long -> KeywordTypeNameModel.Long
        JvmTypeInfo.Short -> KeywordTypeNameModel.Short
        JvmTypeInfo.Boolean -> KeywordTypeNameModel.Boolean
        JvmTypeInfo.Declared -> {
            checkNotNull(type) { "Not reached: type info is absent for declared type" }
            if (type.isError) {
                return ErrorNameModel()
            }
            val declaration = type.classDeclaration() ?: return ErrorNameModel()
            val raw = ClassNameModel(declaration)
            val typeArguments = type.arguments.map { argument ->
                fun argType() = argument.type.resolveOrError()
                when (argument.variance) {
                    Variance.STAR -> WildcardNameModel.Star
                    Variance.INVARIANT -> CtTypeNameModel(argType())
                    Variance.COVARIANT -> WildcardNameModel(upperBound = CtTypeNameModel(argType()))
                    Variance.CONTRAVARIANT -> WildcardNameModel(lowerBound = CtTypeNameModel(argType()))
                }
            }
            return if (typeArguments.isNotEmpty()) {
                ParameterizedNameModel(raw, typeArguments)
            } else raw
        }
        is JvmTypeInfo.Array -> {
            return ArrayNameModel(
                elementType = CtTypeNameModel(
                    type = type?.arguments?.firstOrNull().let {
                        if (it?.variance == Variance.STAR) {
                            // We don't trust KSP to return the correctly mapped type with STAR projection.
                            Utils.objectType.asStarProjectedType()
                        } else {
                            it?.type.resolveOrError()
                        }
                    },
                    jvmTypeKind = jvmTypeKind.elementInfo,
                )
            )
        }
    }
}

private fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val packageName = declaration.packageName.asString()
    return ClassNameModel(
        packageName = packageName,
        simpleNames = declaration.qualifiedName?.asString()
            ?.run {
                if (packageName.isNotEmpty()) substring(packageName.length + 1) else this
            }
            ?.split('.') ?: listOf("<unnamed>"),
    )
}
