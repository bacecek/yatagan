package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.TypeDeclaration
import javax.lang.model.element.TypeElement

fun TypeDeclaration(typeElement: TypeElement): TypeDeclaration {
    return JavaxTypeDeclarationImpl(typeElement.asType().asDeclaredType())
}