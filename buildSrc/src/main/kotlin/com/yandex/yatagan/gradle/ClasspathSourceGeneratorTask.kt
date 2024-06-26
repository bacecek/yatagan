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

package com.yandex.yatagan.gradle

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Generates constant declarations in Kotlin containing configured classpath values.
 *
 * Objects inside [groups] denote a Kotlin `object` container declaration for properties.
 * For each group, objects inside [properties][NamedClasspathGroup.properties] denote a generated property.
 */
@CacheableTask
abstract class ClasspathSourceGeneratorTask @Inject constructor(
    private val objects: ObjectFactory,
) : DefaultTask() {

    /**
     * Kotlin object declaration
     */
    interface NamedClasspathGroup {
        @get:Input
        val name: String

        @get:Nested
        val properties: NamedDomainObjectContainer<NamedClasspath>
    }

    /**
     * `const val` property declaration.
     * [classpath] is represented by a string delimited with [File.pathSeparatorChar].
     */
    interface NamedClasspath {
        @get:Input
        val name: String

        @get:Classpath
        @get:InputFiles
        val classpath: Property<FileCollection>
    }

    /**
     * Generated package name.
     */
    @get:Input
    abstract val packageName: Property<String>

    @get:Nested
    abstract val groups: NamedDomainObjectContainer<NamedClasspathGroup>

    @get:OutputDirectory
    val generatedSourceDir: DirectoryProperty = objects.directoryProperty().apply {
        convention(project.layout.buildDirectory.dir("generated-sources"))
    }

    @get:Input
    val generatedSourceName: Property<String> = objects.property<String>().apply {
        @Suppress("DEPRECATION")
        val defaultName = name.capitalize()
        convention("$defaultName.kt")
    }

    @TaskAction
    fun action() {
        generatedSourceDir.get().asFile.listFiles()?.forEach { it.deleteRecursively() }

        val classpathSeparator = File.pathSeparatorChar
        for (group in groups) {
            val spec = FileSpec.builder(packageName.get(), group.name)
                .addFileComment("This source is GENERATED by gradle task `%L`. Do not edit!", this.name)
                .addType(TypeSpec.objectBuilder(group.name)
                    .run {
                        group.properties.forEach { property ->
                            addProperty(PropertySpec.builder(property.name, STRING, KModifier.CONST)
                                .addAnnotation(AnnotationSpec.builder(Suppress::class).run {
                                    addMember("%S", "ConstPropertyName")
                                    build()
                                })
                                .initializer(CodeBlock.builder().run {
                                    val paths = property.classpath.get().map { it.absolutePath }
                                    paths.forEachIndexed { index, path ->
                                        val isLast = index != paths.lastIndex
                                        add("%S", if (isLast) path + classpathSeparator else path)
                                        if (isLast) add(" +\n")
                                    }
                                    build()
                                }).build()
                            )
                        }
                        build()
                    })
                .build()
            generatedSourceDir.get().file(group.name + ".kt").asFile.writeText(spec.toString())
        }
    }
}

private const val RAW_QUOTE = "\"\"\""