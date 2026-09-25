package com.mynd.watch.studio

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Biblioteca de mostradores com versões. NUNCA apaga nem sobrescreve uma versão.
 * Estrutura em disco (root = context.filesDir/library):
 *   <faceId>/v001.bin, v002.bin, ...
 *   <faceId>/meta.txt      (formato chave=valor, uma versão por linha "ver.N=...")
 *   state.txt              installed=<faceId>:<v>   approved=<faceId>:<v>   (último envio / último aprovado)
 * Código puro (java.io) -> testável na JVM.
 */
class FaceLibrary(private val root: File) {

    data class Version(val n: Int, val created: Long, val sha: String, val note: String, val approved: Boolean)
    data class Entry(val id: String, val name: String, val contract: DataContract, val versions: List<Version>) {
        val latest get() = versions.maxByOrNull { it.n }
        val approved get() = versions.filter { it.approved }.maxByOrNull { it.n }
    }
    data class Ref(val id: String, val v: Int) { override fun toString() = "$id:$v" }

    init { root.mkdirs() }

    private fun dir(id: String) = File(root, id)
    private fun binFile(id: String, v: Int) = File(dir(id), "v%03d.bin".format(v))

    fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "mostrador" }

    // ---------- leitura ----------
    fun list(): List<Entry> = root.listFiles { f -> f.isDirectory }?.mapNotNull { get(it.name) }?.sortedBy { it.name } ?: emptyList()

    fun get(id: String): Entry? {
        val m = File(dir(id), "meta.txt"); if (!m.exists()) return null
        val kv = m.readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val versions = kv.filterKeys { it.startsWith("ver.") }.map { (k, v) ->
            val p = v.split('|', limit = 4)
            Version(k.removePrefix("ver.").toInt(), p[0].toLong(), p[1], p.getOrElse(3) { "" }, p[2] == "1")
        }.sortedBy { it.n }
        return Entry(id, kv["name"] ?: id, DataContract.parse(kv["contract"]), versions)
    }

    fun bytes(ref: Ref): ByteArray = binFile(ref.id, ref.v).readBytes()

    // ---------- escrita ----------
    /** Adiciona versão nova. Se o conteúdo for idêntico a uma versão existente, devolve a existente (não duplica). */
    fun save(name: String, bin: ByteArray, note: String = "", contract: DataContract? = null, id: String = slug(name)): Ref {
        FaceFile.parse(bin)   // garante que é um .bin válido antes de guardar
        val sha = FaceFile.sha(bin)
        val cur = get(id)
        cur?.versions?.firstOrNull { it.sha == sha }?.let { return Ref(id, it.n) }
        val n = (cur?.versions?.maxOfOrNull { it.n } ?: 0) + 1
        dir(id).mkdirs()
        binFile(id, n).writeBytes(bin)
        val versions = (cur?.versions ?: emptyList()) + Version(n, System.currentTimeMillis(), sha, note.replace('\n', ' '), false)
        writeMeta(id, cur?.name ?: name, contract ?: cur?.contract ?: DataContract.REAL_WEATHER, versions)
        return Ref(id, n)
    }

    fun setContract(id: String, c: DataContract) { val e = get(id)!!; writeMeta(id, e.name, c, e.versions) }

    fun markApproved(ref: Ref) {
        val e = get(ref.id)!!
        writeMeta(ref.id, e.name, e.contract, e.versions.map { if (it.n == ref.v) it.copy(approved = true) else it })
        setState("approved", ref)
    }

    fun markInstalled(ref: Ref) = setState("installed", ref)
    fun installed(): Ref? = state("installed")
    fun lastApproved(): Ref? = state("approved")

    private fun writeMeta(id: String, name: String, c: DataContract, versions: List<Version>) {
        val sb = StringBuilder("name=$name\ncontract=${c.serialize()}\n")
        for (v in versions) sb.append("ver.${v.n}=${v.created}|${v.sha}|${if (v.approved) 1 else 0}|${v.note}\n")
        val tmp = File(dir(id), "meta.tmp"); tmp.writeText(sb.toString())
        Files.move(tmp.toPath(), File(dir(id), "meta.txt").toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun stateMap(): MutableMap<String, String> {
        val f = File(root, "state.txt")
        return if (f.exists()) f.readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }.toMutableMap() else mutableMapOf()
    }
    private fun setState(k: String, ref: Ref) {
        val m = stateMap(); m[k] = ref.toString()
        File(root, "state.txt").writeText(m.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    private fun state(k: String): Ref? = stateMap()[k]?.let { val (a, b) = it.split(':'); Ref(a, b.toInt()) }

    // ---------- exportar / importar (backup para PC/Drive) ----------
    fun exportZip(out: OutputStream) = ZipOutputStream(out).use { z ->
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            z.putNextEntry(ZipEntry(f.relativeTo(root).invariantSeparatorsPath)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
        }
    }

    /** Importa um backup. Nunca sobrescreve: arquivos já existentes são mantidos. */
    fun importZip(inp: InputStream) = ZipInputStream(inp).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            val f = File(root, e.name).canonicalFile
            require(f.path.startsWith(root.canonicalPath)) { "zip inválido" }
            if (!e.isDirectory && !f.exists()) { f.parentFile.mkdirs(); f.outputStream().use { z.copyTo(it) } }
        }
    }
}
