package com.example.timetable.data.crypto

/**
 * 敏感字段的键值落地层。抽成接口是为了让 [SecurePrefsStore] 的
 * 迁移、清空、错误映射逻辑能在 JVM 上用内存实现覆盖。
 */
interface SecureKeyValueStore {

    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)

    fun clear()

    fun keys(): Set<String>
}
