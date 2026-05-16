package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst.androidId
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookAnnotation
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookProtagonist
import io.legado.app.data.entities.BookReview
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagGroup
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.ExcludedTag
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RemovedAutoTag
import io.legado.app.data.entities.TagMapping
import io.legado.app.data.entities.readRecord.ReadSession
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.ai.AiDatabase
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.help.book.ReadingMemoryHelper
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.model.BookCover
import io.legado.app.model.VideoPlay.VIDEO_PREF_NAME
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getFile
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.getSharedPreferences
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复
 */
object Restore {

    private val mutex = Mutex()

    private const val TAG = "Restore"

    suspend fun restore(context: Context, uri: Uri) {
        LogUtils.d(TAG, "开始恢复备份 uri:$uri")
        kotlin.runCatching {
            FileUtils.delete(Backup.backupPath)
            if (uri.isContentScheme()) {
                DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                    ZipUtils.unZipToPath(it, Backup.backupPath)
                }
            } else {
                ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
            }
        }.onFailure {
            AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            return
        }
        kotlin.runCatching {
            restoreLocked(Backup.backupPath)
            LocalConfig.lastBackup = System.currentTimeMillis()
        }.onFailure {
            appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
            AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
        }
    }

    suspend fun restoreLocked(path: String) {
        mutex.withLock {
            restore(path)
        }
    }

    private suspend fun restore(path: String) {
        val aes = BackupAES()
        // 先导入书籍，不生成阅读记忆
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: SQLiteConstraintException) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
            // 暂时不生成阅读记忆，等导入readRecord和readSession后再生成
        }
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.deleteAll() //先删除所有,保证和备份数据一样
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }
        
        // 恢复AI服务商配置
        fileToListT<AiProviderEntity>(path, "aiProviders.json")?.let { aiProviders ->
            try {
                val aiDatabase = AiDatabase.getInstance(appCtx)
                val aiDao = aiDatabase.aiDao()
                
                // 插入新的AI服务商配置（使用insertProvider实现Upsert）
                aiProviders.forEach { provider ->
                    aiDao.insertProvider(provider)
                }
                LogUtils.d(TAG, "AI服务商配置恢复完成，数量: ${aiProviders.size}")
            } catch (e: Exception) {
                AppLog.put("恢复AI服务商配置出错\n${e.localizedMessage}", e)
                LogUtils.e(TAG, "AI服务商配置恢复失败: ${e.message}")
            }
        }
        
        // 恢复书籍注解
        fileToListT<BookAnnotation>(path, "bookAnnotation.json")?.let {
            it.forEach { annotation ->
                appDb.bookAnnotationDao.insert(annotation)
            }
        }
        
        // 恢复书籍评论
        fileToListT<BookReview>(path, "bookReview.json")?.let {
            it.forEach { review ->
                appDb.bookReviewDao.insert(review)
            }
        }
        
        // 恢复书籍标签系统
        fileToListT<BookTagGroup>(path, "bookTagGroup.json")?.let {
            it.forEach { tagGroup ->
                appDb.bookTagGroupDao.insert(tagGroup)
            }
        }
        fileToListT<BookTag>(path, "bookTag.json")?.let {
            it.forEach { tag ->
                appDb.bookTagDao.insert(tag)
            }
        }
        fileToListT<BookTagRelation>(path, "bookTagRelation.json")?.let {
            withContext(IO) {
                it.forEach { relation ->
                    appDb.bookTagRelationDao.insert(relation)
                }
            }
        }
        fileToListT<TagMapping>(path, "tagMapping.json")?.let {
            withContext(IO) {
                it.forEach { mapping ->
                    appDb.tagMappingDao.insert(mapping)
                }
            }
        }
        fileToListT<ExcludedTag>(path, "excludedTag.json")?.let {
            withContext(IO) {
                it.forEach { tag ->
                    appDb.excludedTagDao.insert(tag)
                }
            }
        }
        fileToListT<RemovedAutoTag>(path, "removedAutoTag.json")?.let {
            withContext(IO) {
                it.forEach { tag ->
                    appDb.removedAutoTagDao.insert(tag)
                }
            }
        }
        
        // 恢复书籍主角
        fileToListT<BookProtagonist>(path, "bookProtagonist.json")?.let {
            withContext(IO) {
                it.forEach { protagonist ->
                    appDb.bookProtagonistDao.insert(protagonist)
                }
            }
        }
        
        // 先导入readRecord，转换为ReadSession
        // 使用临时数据类来解析旧的ReadRecord结构
        data class OldReadRecord(val bookName: String, val author: String, val bookUrl: String, val readTime: Long, val lastRead: Long)
        fileToListT<OldReadRecord>(path, "readRecord.json")?.let {
            it.forEach { oldRecord ->
                // 将旧的ReadRecord转换为ReadSession
                val readSession = ReadSession(
                    bookName = oldRecord.bookName,
                    author = oldRecord.author,
                    bookUrl = oldRecord.bookUrl,
                    startTime = oldRecord.lastRead - oldRecord.readTime, // 估算开始时间
                    endTime = oldRecord.lastRead,
                    duration = oldRecord.readTime
                )
                
                // 检查是否已存在相似的会话记录
                val existingSessions = appDb.readSessionDao.getByBook(
                    readSession.bookName,
                    readSession.author
                )
                
                val sessionExists = existingSessions.any {
                    it.startTime == readSession.startTime &&
                    it.duration == readSession.duration
                }
                
                if (!sessionExists) {
                    appDb.readSessionDao.insert(readSession)
                }
            }
        }
        
        // 导入readSession（备份中已有的会话记录优先）
        fileToListT<ReadSession>(path, "readSession.json")?.let {
            it.forEach { readSession ->
                // 检查是否已存在相同的会话记录，避免重复导入
                val existingSessions = appDb.readSessionDao.getByBook(
                    readSession.bookName,
                    readSession.author
                )
                
                val sessionExists = existingSessions.any {
                    it.startTime == readSession.startTime &&
                    it.duration == readSession.duration
                }
                
                if (!sessionExists) {
                    appDb.readSessionDao.insert(readSession)
                }
            }
        }
        
        // 恢复阅读记忆记录
        fileToListT<ReadingMemory>(path, "readingMemory.json")?.let {
            it.forEach { readingMemory ->
                // 检查是否已存在相同id的记录
                val existingMemory = appDb.readingMemoryDao.getMemoryById(readingMemory.id)
                if (existingMemory != null) {
                    // 已存在，更新记录
                    appDb.readingMemoryDao.update(readingMemory)
                } else {
                    // 不存在，插入新记录
                    appDb.readingMemoryDao.insert(readingMemory)
                }
                
                // 同步阅读记忆中的finishReadTime到Book中，确保数据一致性
                val book = appDb.bookDao.getBook(readingMemory.bookUrl)
                if (book != null && readingMemory.finishReadTime > 0L && book.finishReadTime == 0L) {
                    book.finishReadTime = readingMemory.finishReadTime
                    appDb.bookDao.update(book)
                }
            }
        }
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }
        //恢复主题配置
        File(path, ThemeConfig.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            FileUtils.delete(ThemeConfig.configFilePath)
            copyTo(File(ThemeConfig.configFilePath))
            ThemeConfig.upConfig()
        }?.onFailure {
            AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
        }
        restoreThemeBgImages(path)
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }
        if (!BackupConfig.ignoreReadConfig) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }
        //AppWebDav.downBgs()
        appCtx.getSharedPreferences(path, "config")?.all?.let { map ->
            val edit = appCtx.defaultSharedPreferences.edit()

            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        PreferKey.webDavPassword -> {
                            kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull()?.let {
                                edit.putString(key, it)
                            } ?: let {
                                if (appCtx.getPrefString(PreferKey.webDavPassword)
                                        .isNullOrBlank()
                                ) {
                                    edit.putString(key, value.toString())
                                }
                            }
                        }

                        else -> when (value) {
                            is Int -> edit.putInt(key, value)
                            is Boolean -> edit.putBoolean(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                        }
                    }
                }
            }
            edit.apply()
        }
        appCtx.getSharedPreferences(path, "videoConfig")?.all?.let { map ->
            appCtx.getSharedPreferences(VIDEO_PREF_NAME, Context.MODE_PRIVATE).edit().apply {
                map.forEach { (key, value) ->
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                    }
                }
                apply()
            }
        }
        ReadBookConfig.apply {
            comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect)
            readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
            shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout)
            hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
            hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
            autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, 46)
        }
        // 备份恢复完成后，一次性处理所有旧数据，将ReadRecord转换为ReadSession
        // 确保旧数据被正确处理，避免后续触发转换时出现问题
        val allBooks = appDb.bookDao.all
        allBooks.forEach {
            ReadingMemoryHelper.createReadingMemory(it)
        }
        
        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfig.applyDayNight(appCtx)
        }
    }

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

    private fun restoreThemeBgImages(path: String) {
        val themeBgDir = File(path, "themeBg")
        if (!themeBgDir.exists() || !themeBgDir.isDirectory) {
            return
        }
        val files = themeBgDir.listFiles() ?: return
        if (files.isEmpty()) {
            return
        }
        files.forEach { file ->
            val targetDir = when {
                file.name.startsWith("night_") || file.parentFile?.name == PreferKey.bgImageN -> {
                    appCtx.externalFiles.getFile(PreferKey.bgImageN)
                }
                else -> {
                    appCtx.externalFiles.getFile(PreferKey.bgImage)
                }
            }
            targetDir.mkdirs()
            file.copyTo(File(targetDir, file.name), overwrite = true)
        }
        LogUtils.d(TAG, "主题背景图片恢复完成，数量: ${files.size}")
    }

}