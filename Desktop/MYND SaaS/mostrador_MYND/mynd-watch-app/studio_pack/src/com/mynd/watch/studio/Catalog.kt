package com.mynd.watch.studio

/**
 * Catálogo do firmware (validado no relógio com as sondas A, B, C e E).
 * NÃO adicionar tipos aqui sem validar no relógio.
 */
object Catalog {

    enum class Src { WATCH, WEATHER_CURRENT, WEATHER_PAIR, WEATHER_ICON, FIXED }

    data class TextType(val id: Int, val nome: String, val exemplo: String, val src: Src)

    val TEXT_TYPES: List<TextType> = listOf(
        TextType(1, "Hora (dezena)", "1", Src.WATCH), TextType(2, "Hora (unidade)", "6", Src.WATCH),
        TextType(3, "Minuto (dezena)", "3", Src.WATCH), TextType(4, "Minuto (unidade)", "8", Src.WATCH),
        TextType(5, "Batimento (BPM)", "91", Src.WATCH), TextType(6, "Ano", "2026", Src.WATCH),
        TextType(7, "Mês (número)", "09", Src.WATCH), TextType(8, "Dia do mês", "24", Src.WATCH),
        TextType(9, "Dia da semana (THU)", "THU", Src.WATCH),
        TextType(13, "Clima: atual + °C", "34°C", Src.WEATHER_CURRENT),
        TextType(14, "Passos", "7420", Src.WATCH), TextType(16, "Desconhecido (0)", "0", Src.WATCH),
        TextType(17, "Calorias", "328", Src.WATCH), TextType(18, "Bateria %", "78%", Src.WATCH),
        TextType(19, "Texto fixo", "MYND", Src.FIXED), TextType(21, "Bateria (número)", "78", Src.WATCH),
        TextType(22, "Segundos", "32", Src.WATCH), TextType(23, "Distância km", "0.21", Src.WATCH),
        TextType(24, "Clima: atual + °", "34°", Src.WEATHER_CURRENT),
        TextType(25, "Mês (Sep)", "Sep", Src.WATCH), TextType(26, "Mês (September)", "September", Src.WATCH),
        TextType(27, "Mês (SEP)", "SEP", Src.WATCH), TextType(28, "Dia da semana (Thu)", "Thu", Src.WATCH),
        TextType(29, "Dia da semana (THURSDAY)", "THURSDAY", Src.WATCH),
        TextType(30, "Minutos (2 dígitos)", "47", Src.WATCH), TextType(31, "AM/PM", "PM", Src.WATCH),
        TextType(32, "Hora (2 dígitos)", "10", Src.WATCH),
        TextType(33, "Clima: mín/máx", "14/4°C", Src.WEATHER_PAIR),
        TextType(34, "Clima: descrição", "Nublado", Src.WEATHER_ICON),
    )
    val TEXT_BY_ID = TEXT_TYPES.associateBy { it.id }

    data class ImageType(val id: Int, val nome: String, val minImagens: Int, val src: Src)

    val IMAGE_TYPES: List<ImageType> = listOf(
        ImageType(1, "Hora dezena", 3, Src.WATCH), ImageType(2, "Hora unidade", 10, Src.WATCH),
        ImageType(3, "Minuto dezena", 6, Src.WATCH), ImageType(4, "Minuto unidade", 10, Src.WATCH),
        ImageType(6, "Ícone do clima (0..15)", 16, Src.WEATHER_ICON),
        ImageType(8, "Mês dezena", 2, Src.WATCH), ImageType(9, "Mês unidade", 10, Src.WATCH),
        ImageType(10, "Dia dezena", 4, Src.WATCH), ImageType(11, "Dia unidade", 10, Src.WATCH),
        ImageType(23, "Bateria (6 níveis)", 6, Src.WATCH), ImageType(24, "AM/PM", 2, Src.WATCH),
        ImageType(31, "Dia da semana (DOM..SÁB)", 7, Src.WATCH),
    )
    val IMAGE_BY_ID = IMAGE_TYPES.associateBy { it.id }

    /** Altura aproximada da fonte do firmware (px) para colisão/prévia. */
    const val FONT_H = 20
    /** Largura aproximada por caractere da fonte do firmware (px). */
    const val CHAR_W = 11
}
