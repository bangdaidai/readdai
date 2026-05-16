package io.legado.app.help.ai

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import io.legado.app.help.ai.AiProviderEntity
import io.legado.app.help.ai.AiPromptEntity
import io.legado.app.help.ai.AiRecallCacheEntity
import io.legado.app.help.ai.AiSkillEntity
import io.legado.app.help.ai.AiModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AI数据访问对象
 */
@Dao
interface AiDao {
    // 服务商配置
    @Query("SELECT * FROM ai_providers ORDER BY identifier")
    suspend fun getAllProviders(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE identifier = :identifier")
    suspend fun getProvider(identifier: String): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AiProviderEntity)

    @Query("DELETE FROM ai_providers WHERE identifier = :identifier")
    suspend fun deleteProvider(identifier: String)

    // 提示词配置（兼容旧版）
    @Query("SELECT * FROM ai_custom_prompts WHERE showIn LIKE '%' || :entrance || '%' ORDER BY sortOrder")
    suspend fun getPromptsByEntrance(entrance: String): List<AiPromptEntity>

    @Query("SELECT * FROM ai_custom_prompts ORDER BY sortOrder")
    suspend fun getAllPrompts(): List<AiPromptEntity>

    @Query("SELECT * FROM ai_custom_prompts WHERE id = :id")
    suspend fun getPrompt(id: String): AiPromptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: AiPromptEntity)

    @Query("DELETE FROM ai_custom_prompts WHERE id = :id")
    suspend fun deletePrompt(id: String)

    @Query("UPDATE ai_custom_prompts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setPromptEnabled(id: String, enabled: Boolean)

    // 技能配置（新版Skills系统）
    @Query("SELECT * FROM ai_skills WHERE showIn LIKE '%' || :entrance || '%' AND isEnabled = 1 ORDER BY sortOrder")
    suspend fun getSkillsByEntrance(entrance: String): List<AiSkillEntity>

    @Query("SELECT * FROM ai_skills ORDER BY sortOrder")
    suspend fun getAllSkills(): List<AiSkillEntity>

    @Query("SELECT * FROM ai_skills WHERE id = :id")
    suspend fun getSkill(id: String): AiSkillEntity?

    @Query("SELECT * FROM ai_skills WHERE category = :category AND isEnabled = 1 ORDER BY sortOrder")
    suspend fun getSkillsByCategory(category: String): List<AiSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: AiSkillEntity)

    @Query("DELETE FROM ai_skills WHERE id = :id")
    suspend fun deleteSkill(id: String)

    @Query("UPDATE ai_skills SET isEnabled = :enabled WHERE id = :id")
    suspend fun setSkillEnabled(id: String, enabled: Boolean)

    @Query("UPDATE ai_skills SET sortOrder = :order WHERE id = :id")
    suspend fun updateSkillOrder(id: String, order: Int)

    // 模型配置（参照archive项目设计）
    @Query("SELECT * FROM ai_models WHERE providerId = :providerId ORDER BY sortOrder, createdAt")
    suspend fun getModelsByProvider(providerId: String): List<AiModelConfig>

    @Query("SELECT * FROM ai_models WHERE id = :id")
    suspend fun getModel(id: String): AiModelConfig?

    @Query("SELECT * FROM ai_models WHERE providerId = :providerId AND modelId = :modelId LIMIT 1")
    suspend fun getModelByProviderAndId(providerId: String, modelId: String): AiModelConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AiModelConfig)

    @Query("DELETE FROM ai_models WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("DELETE FROM ai_models WHERE providerId = :providerId")
    suspend fun deleteModelsByProvider(providerId: String)

    @Query("UPDATE ai_models SET enabled = :enabled WHERE id = :id")
    suspend fun setModelEnabled(id: String, enabled: Boolean)

    // 前情提要缓存
    @Query("SELECT * FROM ai_recall_cache WHERE bookUrl = :bookUrl")
    suspend fun getRecallCache(bookUrl: String): AiRecallCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecallCache(cache: AiRecallCacheEntity)

    @Query("DELETE FROM ai_recall_cache WHERE bookUrl = :bookUrl")
    suspend fun deleteRecallCache(bookUrl: String)

    @Query("SELECT COUNT(*) FROM ai_recall_cache")
    suspend fun getRecallCacheCount(): Int

    @Query("DELETE FROM ai_recall_cache")
    suspend fun clearRecallCache()
}

/**
 * AI数据库
 */
@Database(
    entities = [
        AiProviderEntity::class,
        AiModelConfig::class,
        AiPromptEntity::class,
        AiSkillEntity::class,
        AiRecallCacheEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AiDatabase : RoomDatabase() {
    abstract fun aiDao(): AiDao

    companion object {
        @Volatile
        private var INSTANCE: AiDatabase? = null

        fun getInstance(context: Context): AiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * 对话历史存储
 * 参照anx53的AiHistoryStore，使用JSON文件存储
 */
object AiHistoryStore {
    private const val historyFileName = "ai_chat_history.json"
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    private fun getHistoryFile(): File {
        return File(context?.cacheDir, historyFileName)
    }

    suspend fun readHistory(): List<AiChatSession> = withContext(Dispatchers.IO) {
        val file = getHistoryFile()
        if (!file.exists()) {
            return@withContext emptyList()
        }

        try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val sessions = mutableListOf<AiChatSession>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                sessions.add(AiChatSession.fromJson(jsonObject.toMap()))
            }
            sessions
        } catch (e: Exception) {
            file.delete()
            emptyList()
        }
    }

    suspend fun upsertSession(session: AiChatSession) = withContext(Dispatchers.IO) {
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiHistory", "保存会话: id=${session.id}, messages=${session.messages.size}, completed=${session.completed}")
        
        val history = readHistory().toMutableList()
        val existingIndex = history.indexOfFirst { it.id == session.id }

        if (existingIndex >= 0) {
            history[existingIndex] = session
        } else {
            history.add(session)
        }

        history.sortByDescending { it.updatedAt }
        val limited = history.take(100)

        val jsonArray = JSONArray()
        limited.forEach { session ->
            jsonArray.put(JSONObject(session.toJson()))
        }

        getHistoryFile().writeText(jsonArray.toString())
        AiLogManager.log(AiLogManager.LogLevel.DEBUG, "AiHistory", "会话保存成功: 总会话数=${limited.size}")
    }

    suspend fun removeSession(id: String) = withContext(Dispatchers.IO) {
        val history = readHistory().filter { it.id != id }
        val jsonArray = JSONArray()
        history.forEach { session ->
            jsonArray.put(JSONObject(session.toJson()))
        }
        getHistoryFile().writeText(jsonArray.toString())
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        getHistoryFile().delete()
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            val value = get(key)
            when (value) {
                is JSONObject -> map[key] = value.toMap()
                is JSONArray -> map[key] = value.toList()
                else -> map[key] = value
            }
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until length()) {
            val item = get(i)
            when (item) {
                is JSONObject -> list.add(item.toMap())
                is JSONArray -> list.add(item.toList())
                else -> list.add(item)
            }
        }
        return list
    }
}
