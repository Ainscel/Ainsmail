package com.ainscel.ainsmail.model

/**
 * 一个词只属于一个类型的一个池。所有账号共用。
 * 衰减、过期和升入活跃池以后写在旁边的纯函数里，不写进邮件记录。
 */
data class TagWord(
    val name: String,
    val type: MailType,
    val pool: TagPool,
)

data class TagCatalog(
    val words: List<TagWord>,
) {
    fun find(name: String): TagWord? = words.find { it.name == name }

    fun poolOf(name: String): TagPool? = find(name)?.pool

    /** 词已经在目录里时不动。新词进指定类型的待定池。 */
    fun withPending(name: String, type: MailType): TagCatalog {
        if (find(name) != null) return this
        return copy(words = words + TagWord(name, type, TagPool.Pending))
    }
}
