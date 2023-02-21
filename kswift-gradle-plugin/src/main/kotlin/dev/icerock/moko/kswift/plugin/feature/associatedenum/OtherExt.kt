package dev.icerock.moko.kswift.plugin.feature.associatedenum

import dev.icerock.moko.kswift.plugin.objcNameToSwift
import io.outfoxx.swiftpoet.ANY_OBJECT
import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.DICTIONARY
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionTypeName
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.SET
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.VOID
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection

private val NSSTRING = DeclaredTypeName(moduleName = "Foundation", simpleName = "NSString")

internal fun KmType.kotlinTypeNameToInner(
    moduleName: String,
    namingMode: NamingMode,
    isOuterSwift: Boolean,
): TypeName? {
    val typeName = this.nameAsString
    return when {
        typeName == null -> null
        typeName.startsWith("kotlin/") -> {
            when (namingMode) {
                NamingMode.KOTLIN -> typeName.kotlinPrimitiveTypeNameToKotlinInterop(moduleName)
                NamingMode.SWIFT -> typeName.kotlinPrimitiveTypeNameToSwift(moduleName, arguments)
                NamingMode.OBJC -> typeName.kotlinPrimitiveTypeNameToObjectiveC(moduleName)
                NamingMode.KOTLIN_NO_STRING ->
                    typeName
                        .kotlinPrimitiveTypeNameToKotlinInterop(moduleName)
                        .let { if (it == STRING) NSSTRING else it }
            }
        }

        else -> getDeclaredTypeNameFromNonPrimitive(typeName, moduleName)
    }?.addGenericsAndOptional(
        kmType = this,
        moduleName = moduleName,
        namingMode = namingMode,
        isOuterSwift = isOuterSwift,
    )
}

private fun String.kotlinPrimitiveTypeNameToSwift(
    moduleName: String,
    arguments: List<KmTypeProjection>,
): TypeName {
    require(this.startsWith("kotlin/"))
    return when (this) {
        "kotlin/Char" -> DeclaredTypeName.typeName("Swift.Character")
        "kotlin/Comparable" -> DeclaredTypeName.typeName("Swift.Comparable")
        "kotlin/Pair" -> arguments.generateTupleType(moduleName)
        "kotlin/Result" -> ANY_OBJECT
        "kotlin/String" -> STRING
        "kotlin/Triple" -> arguments.generateTupleType(moduleName)
        "kotlin/Throwable" -> DeclaredTypeName(
            moduleName = moduleName,
            simpleName = "KotlinThrowable",
        )

        "kotlin/Unit" -> VOID
        "kotlin/collections/List" -> ARRAY
        "kotlin/collections/Map" -> DICTIONARY
        "kotlin/collections/Set" -> SET
        else -> {
            if (this.startsWith("kotlin/Function")) {
                val typedArgs = arguments.getTypes(moduleName, NamingMode.KOTLIN, false)
                val types = typedArgs.map { ParameterSpec.unnamed(it) }.dropLast(1)
                FunctionTypeName.get(types, typedArgs.last())
            } else {
                kotlinToSwiftTypeMap[this] ?: this.kotlinInteropName(moduleName)
            }
        }
    }
}

internal fun KmType.kotlinPrimitiveTypeNameToSwift(moduleName: String): TypeName? {
    val typeName = this.nameAsString

    return when {
        typeName == null -> null
        typeName.startsWith("kotlin/") ->
            typeName.kotlinPrimitiveTypeNameToSwift(moduleName, this.arguments)

        else -> getDeclaredTypeNameFromNonPrimitive(typeName, moduleName)
    }?.addGenericsAndOptional(
        kmType = this,
        moduleName = moduleName,
        namingMode = null,
        isOuterSwift = true,
    )
}

private val KmType.nameAsString: String?
    get() = when (val classifier = this.classifier) {
        is KmClassifier.Class -> classifier.name
        is KmClassifier.TypeParameter -> null
        is KmClassifier.TypeAlias -> classifier.name
    }

private fun String.kotlinPrimitiveTypeNameToKotlinInterop(moduleName: String): TypeName {
    require(this.startsWith("kotlin/"))
    return when (this) {
        "kotlin/String" -> STRING
        "kotlin/collections/List" -> ARRAY
        "kotlin/collections/Map" -> DICTIONARY
        "kotlin/collections/Set" -> SET
        else -> this.kotlinInteropName(moduleName)
    }
}

private fun String.kotlinInteropName(moduleName: String) = DeclaredTypeName(
    moduleName = moduleName,
    simpleName = "Kotlin" + this.split("/").last(),
)

private fun String.kotlinPrimitiveTypeNameToObjectiveC(moduleName: String): DeclaredTypeName {
    require(this.startsWith("kotlin/"))
    return when (this) {
        "kotlin/Any" -> ANY_OBJECT
        "kotlin/Boolean" -> DeclaredTypeName(moduleName = moduleName, simpleName = "KotlinBoolean")
        "kotlin/Pair" -> DeclaredTypeName(moduleName = moduleName, simpleName = "KotlinPair")
        "kotlin/Result" -> ANY_OBJECT
        "kotlin/String" -> NSSTRING
        "kotlin/Short" -> DeclaredTypeName(moduleName = "Foundation", simpleName = "NSNumber")
        "kotlin/Triple" -> DeclaredTypeName(moduleName = moduleName, simpleName = "KotlinTriple")
        "kotlin/collections/Map" -> DeclaredTypeName(
            moduleName = "Foundation",
            simpleName = "NSDictionary",
        )

        "kotlin/collections/Set" -> DeclaredTypeName(
            moduleName = "Foundation",
            simpleName = "NSSet",
        )

        "kotlin/collections/List" -> DeclaredTypeName(
            moduleName = "Foundation",
            simpleName = "NSArray",
        )

        else -> this.kotlinInteropName(moduleName)
    }
}

private fun getDeclaredTypeNameFromNonPrimitive(
    typeName: String,
    moduleName: String,
) = if (typeName.startsWith("platform/")) {
    val withoutCompanion: String = typeName.removeSuffix(".Companion")
    val moduleAndClass: List<String> = withoutCompanion.split("/").drop(1)
    val module: String = moduleAndClass[0]
    val className: String = moduleAndClass[1]

    DeclaredTypeName.typeName(
        listOf(module, className).joinToString("."),
    ).objcNameToSwift()
} else {
    // take type after final slash and generate declared type assuming module name
    val simpleName: String = typeName.split("/").last()
    DeclaredTypeName(
        moduleName = moduleName,
        simpleName = simpleName,
    )
}