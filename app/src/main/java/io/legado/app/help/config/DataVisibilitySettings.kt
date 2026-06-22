package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import splitties.init.appCtx
import io.legado.app.utils.getPrefBoolean

object DataVisibilitySettings {
    fun isBasicInfoVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowBasicInfo, true)
    fun isProgressVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowProgress, true)
    fun isStatisticsVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowStatistics, true)
    fun isRatingReviewVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowRatingReview, true)
    fun isAnnotationVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowAnnotation, true)
    fun isProtagonistVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowProtagonist, false)
    fun isTagsVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowTags, false)
    fun isSourceVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowSource, false)
    fun isRankVisible(): Boolean = appCtx.getPrefBoolean(PreferKey.bpShowRank, false)
}
