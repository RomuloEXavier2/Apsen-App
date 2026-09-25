package com.mynd.watch.studio

/**
 * Verificador executado SEMPRE antes de enviar. Qualquer ERRO bloqueia o botão Enviar.
 * Cada regra existe porque o problema já aconteceu de verdade neste projeto.
 */
object FaceValidator {
    enum class Level { ERRO, AVISO }
    data class Issue(val level: Level, val msg: String)

    const val SIZE_WARN = 530_000   // maior mostrador aceito até hoje: ~523 KB

    fun check(f: FaceFile, contract: DataContract? = null, fileSize: Int? = null): List<Issue> {
        val out = mutableListOf<Issue>()
        fun erro(m: String) { out += Issue(Level.ERRO, m) }
        fun aviso(m: String) { out += Issue(Level.AVISO, m) }

        if (f.images.size + f.texts.size > JL.MAX_RECORDS) erro("Mais de ${JL.MAX_RECORDS} elementos.")
        if (f.tableBytes > JL.TABLE_BYTES) erro("Tabela cheia: ${f.tableBytes}/${JL.TABLE_BYTES} bytes (cada imagem 132, cada texto 32).")

        for (im in f.images) {
            val t = Catalog.IMAGE_BY_ID[im.type]
            if (t == null) erro("Imagem tipo ${im.type} não existe no firmware.")
            else if (im.sprites.size < t.minImagens) erro("${t.nome}: precisa de ${t.minImagens} imagens, tem ${im.sprites.size}.")
            for (s in im.sprites) if (s.rowBytes % 4 != 0) erro("${t?.nome ?: im.type}: largura ${s.w}px gera linha de ${s.rowBytes} bytes (tem que ser múltiplo de 4) -> cores trocadas/serrilhado.")
            val s0 = im.sprites.firstOrNull()
            if (s0 != null && (im.x < 0 || im.y < 0 || im.x + s0.w > JL.W || im.y + s0.h > JL.H)) erro("${t?.nome ?: im.type}: sai da tela.")
        }
        for (tx in f.texts) {
            val t = Catalog.TEXT_BY_ID[tx.type]
            if (t == null) erro("Campo de texto tipo ${tx.type} não existe (o relógio mostra \"Text\").")
            if (tx.x < 0 || tx.y < 0 || tx.x + tx.width > JL.W || tx.y + Catalog.FONT_H > JL.H) erro("Campo ${t?.nome ?: tx.type} sai da tela.")
            if (tx.width < Catalog.CHAR_W) aviso("Campo ${t?.nome ?: tx.type}: largura ${tx.width}px muito pequena.")
        }
        // sobreposição entre campos de texto
        for (i in f.texts.indices) for (j in i + 1 until f.texts.size) {
            val a = f.texts[i]; val b = f.texts[j]
            if (a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + Catalog.FONT_H && b.y < a.y + Catalog.FONT_H)
                aviso("Campos ${Catalog.TEXT_BY_ID[a.type]?.nome ?: a.type} e ${Catalog.TEXT_BY_ID[b.type]?.nome ?: b.type} se sobrepõem.")
        }
        // contrato x campos usados
        if (contract != null) {
            val usesPair = f.texts.any { it.type == 33 }
            val usesCur = f.texts.any { it.type == 13 || it.type == 24 }
            val usesIcon = f.images.any { it.type == 6 } || f.texts.any { it.type == 34 }
            if (usesCur && contract.current == CurrentSrc.REAL_WEATHER && contract.usesBambu) aviso("Canal 'atual' usado no mostrador mas o contrato manda clima real.")
            if (!usesCur && contract.current != CurrentSrc.REAL_WEATHER) aviso("Contrato manda ${contract.current} mas nenhum campo 13/24 mostra.")
            if (!usesPair && contract.pair != PairSrc.REAL_WEATHER) aviso("Contrato manda ${contract.pair} mas nenhum campo 33 mostra.")
            if (!usesIcon && contract.icon != IconSrc.REAL_WEATHER) aviso("Contrato manda ${contract.icon} mas não há imagem 6 / texto 34.")
            if (contract.icon == IconSrc.BAMBU_NOZZLE_LEVEL && f.images.none { it.type == 6 }) erro("Contrato BICO por imagem, mas o mostrador não tem imagem tipo 6.")
        }
        val size = fileSize ?: runCatching { f.build().size }.getOrDefault(0)
        if (size > SIZE_WARN) aviso("Arquivo com ${size / 1024} KB (acima de ~520 KB ainda não foi testado).")
        return out
    }

    fun hasErrors(issues: List<Issue>) = issues.any { it.level == Level.ERRO }
}
