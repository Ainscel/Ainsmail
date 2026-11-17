package com.ainscel.ainsmail.data.imap

import android.util.Log
import com.ainscel.ainsmail.model.RemoteFolder
import jakarta.mail.Folder
import jakarta.mail.Store
import org.eclipse.angus.mail.imap.IMAPFolder

/**
 * 板块 → 服务器夹路径。
 * 优先 SPECIAL-USE（`\Drafts` / `\Sent` / `\Trash`），再试常见中英文名。
 * 对不上的板块不进结果，上层显示空列表，不把信归错。
 */
fun resolveRemoteFolders(store: Store): Map<RemoteFolder, String> {
    val result = linkedMapOf<RemoteFolder, String>()
    result[RemoteFolder.Inbox] = "INBOX"

    val listed = try {
        store.defaultFolder.list("*")
    } catch (error: Exception) {
        Log.w("AinsmailSync", "list_folders ${error.javaClass.simpleName}: ${error.message}")
        emptyArray()
    }

    fun pick(target: RemoteFolder, special: String, names: List<String>) {
        if (result.containsKey(target)) return
        val bySpecial = listed.firstOrNull { folder ->
            folderSpecialUses(folder).any { it.equals(special, ignoreCase = true) }
        }
        if (bySpecial != null) {
            result[target] = bySpecial.fullName
            return
        }
        val byName = listed.firstOrNull { folder ->
            names.any { candidate -> folder.fullName.equals(candidate, ignoreCase = true) }
        }
        if (byName != null) result[target] = byName.fullName
    }

    pick(
        RemoteFolder.Drafts,
        "\\Drafts",
        listOf("Drafts", "Draft", "草稿箱", "草稿"),
    )
    pick(
        RemoteFolder.Sent,
        "\\Sent",
        listOf("Sent", "Sent Messages", "Sent Mail", "[Gmail]/Sent Mail", "已发送", "已发送邮件"),
    )
    pick(
        RemoteFolder.Trash,
        "\\Trash",
        listOf("Trash", "Deleted Messages", "[Gmail]/Trash", "已删除", "删除", "垃圾箱"),
    )
    return result
}

/** 列出 SPECIAL-USE 时用；普通 Folder 没有 attributes 就当没有。 */
internal fun folderSpecialUses(folder: Folder): Array<String> {
    return try {
        (folder as? IMAPFolder)?.attributes ?: emptyArray()
    } catch (_: Exception) {
        emptyArray()
    }
}
