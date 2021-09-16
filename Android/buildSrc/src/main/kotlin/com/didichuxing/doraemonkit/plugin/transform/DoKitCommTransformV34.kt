package com.didichuxing.doraemonkit.plugin.transform

import com.android.build.api.variant.VariantInfo
import com.didichuxing.doraemonkit.plugin.DoKitExtUtil
import com.didichuxing.doraemonkit.plugin.asmtransformer.DoKitAsmTransformer
import com.didichuxing.doraemonkit.plugin.classtransformer.*
import com.didichuxing.doraemonkit.plugin.extension.SlowMethodExt
import com.didichuxing.doraemonkit.plugin.stack_method.MethodStackNodeUtil
import com.didiglobal.booster.transform.Transformer
import org.gradle.api.Project

internal class DoKitCommTransformV34(project: Project) : DoKitBaseTransform(project) {


    override val transformers = listOf<Transformer>(
        DoKitAsmTransformer(
            listOf(
                CommClassTransformer(),
                BigImgClassTransformer(),
                GSMClassTransformer(),
                EnterMSClassTransformer()
            )
        )
    )

    @Suppress("UnstableApiUsage")
    override fun applyToVariant(variant: VariantInfo): Boolean {
        return variant.buildTypeEnabled || (variant.flavorNames.isNotEmpty() && variant.fullVariantEnabled)
    }

    @Suppress("UnstableApiUsage")
    private val VariantInfo.fullVariantEnabled: Boolean
        get() = project.findProperty("booster.transform.${fullVariantName}.enabled")?.toString()?.toBoolean() ?: true

    @Suppress("UnstableApiUsage")
    private val VariantInfo.buildTypeEnabled: Boolean
        get() = project.findProperty("booster.transform.${buildTypeName}.enabled")?.toString()?.toBoolean() ?: true

}

