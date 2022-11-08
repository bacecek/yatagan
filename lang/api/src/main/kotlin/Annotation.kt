package com.yandex.yatagan.lang

/**
 * Models annotation instance of any class.
 */
interface Annotation : HasPlatformModel {
    /**
     * Annotation class declaration.
     */
    val annotationClass: AnnotationDeclaration

    /**
     * Get the value of an [attribute], returning default value if no explicit value was specified.
     *
     * Please note, that using this method for obtaining attributes values is only justified for user-defined
     * annotations, whose definition is unknown to the framework.
     * For framework annotations use corresponding concrete annotation models, that may optimize the way they obtain
     *  the attributes, like e.g. [BuiltinAnnotation.Component], [BuiltinAnnotation.Module], ...
     *
     * @param attribute one of this model's [annotationClass]'s [attributes][AnnotationDeclaration.attributes].
     *  Otherwise, behaviour is undefined.
     */
    fun getValue(attribute: AnnotationDeclaration.Attribute): Value

    /**
     * Models annotation attribute value.
     */
    interface Value : HasPlatformModel {
        interface Visitor<R> {
            fun visitBoolean(value: Boolean): R
            fun visitByte(value: Byte): R
            fun visitShort(value: Short): R
            fun visitInt(value: Int): R
            fun visitLong(value: Long): R
            fun visitChar(value: Char): R
            fun visitFloat(value: Float): R
            fun visitDouble(value: Double): R
            fun visitString(value: String): R
            fun visitType(value: Type): R
            fun visitAnnotation(value: Annotation): R
            fun visitEnumConstant(enum: Type, constant: String): R
            fun visitArray(value: List<Value>): R
            fun visitUnresolved(): R
        }

        fun <R> accept(visitor: Visitor<R>): R
    }
}

