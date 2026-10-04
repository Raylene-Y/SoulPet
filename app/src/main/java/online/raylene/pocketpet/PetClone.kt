package online.raylene.pocketpet

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 宠物克隆：files/pet/ 整个目录 ↔ zip。
 * 导出 = 打包分享；导入 = 解包覆盖（对方的宠物在你手机里复活）。
 */
object PetClone {

    private const val VERSION_KEY = "soulpet_clone_version"
    private const val VERSION = 1

    fun export(ctx: Context, outFile: File): Boolean {
        return try {
            val petDir = File(ctx.filesDir, "pet")
            if (!petDir.exists()) return false
            outFile.parentFile?.mkdirs()
            ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
                // 版本标记
                zos.putNextEntry(ZipEntry(".clone_meta"))
                zos.write("$VERSION_KEY=$VERSION".toByteArray())
                zos.closeEntry()
                petDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(petDir).path
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) { false }
    }

    fun import(ctx: Context, uri: Uri): String {
        return try {
            val petDir = File(ctx.filesDir, "pet")
            var count = 0
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == ".clone_meta") { entry = zis.nextEntry; continue }
                        // 防 zip 路径穿越
                        val target = File(petDir, entry.name).canonicalFile
                        if (!target.path.startsWith(petDir.canonicalPath)) {
                            entry = zis.nextEntry; continue
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zis.copyTo(it) }
                        count++
                        entry = zis.nextEntry
                    }
                }
            } ?: return "读不到文件"
            "克隆完成，$count 个记忆/人格文件已复活。重开聊天窗生效"
        } catch (e: Exception) {
            "导入失败：${e.message}"
        }
    }
}
