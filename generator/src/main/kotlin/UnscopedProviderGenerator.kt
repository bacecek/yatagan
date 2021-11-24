package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class UnscopedProviderGenerator(
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    val name: ClassName = componentImplName.nestedClass("FactoryImpl")

    override fun generate(builder: TypeSpecBuilder) {
        builder.nestedType {
            buildClass(name) {
                implements(Names.Provider)
                modifiers(PRIVATE, STATIC, FINAL)
                field(componentImplName, "mFactory") {
                    modifiers(PRIVATE, FINAL)
                }
                field(ClassName.INT, "mIndex") {
                    modifiers(PRIVATE, FINAL)
                }
                constructor {
                    parameter(componentImplName, "factory")
                    parameter(ClassName.INT, "index")
                    +"mFactory = factory"
                    +"mIndex = index"
                }
                method("get") {
                    modifiers(PUBLIC)
                    annotation<Override>()
                    returnType(ClassName.OBJECT)
                    +"return mFactory.%N(mIndex)".formatCode(SlotSwitchingGenerator.FactoryMethodName)
                }
            }
        }
    }
}