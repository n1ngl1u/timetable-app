package com.example.timetable.domain.importer

/**
 * 本地导入（content:// / file:// / 流）读取失败。与网络型适配器抛出的 IOException 区分开，
 * 导入编排据此映射为 ImportResult.InvalidInput，而不是误报为网络异常。
 */
class ImportIoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
