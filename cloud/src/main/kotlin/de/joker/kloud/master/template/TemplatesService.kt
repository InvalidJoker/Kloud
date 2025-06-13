package de.joker.kloud.master.template

import build.buf.gen.generic.v1.GenericRequest
import build.buf.gen.templates.v1.TemplateList
import build.buf.gen.templates.v1.TemplateServiceGrpcKt
import org.koin.java.KoinJavaComponent

class TemplatesService : TemplateServiceGrpcKt.TemplateServiceCoroutineImplBase() {
    override suspend fun listTemplates(request: GenericRequest): TemplateList {
        val manager: TemplateManager by KoinJavaComponent.inject(TemplateManager::class.java)

        val templates = manager.listTemplates().map { it.toProto() }

        return TemplateList.newBuilder()
            .addAllTemplates(templates)
            .build()
    }
}