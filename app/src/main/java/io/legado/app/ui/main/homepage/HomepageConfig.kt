package io.legado.app.ui.main.homepage

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

object HomepageConfig {

    var homepageLayoutMode: Int
        get() = appCtx.getPrefInt(PreferKey.homepageLayoutMode, 0)
        set(value) = appCtx.putPrefInt(PreferKey.homepageLayoutMode, value)

    var homepageSourceHidden: String
        get() = appCtx.getPrefString("homepageSourceHidden", "") ?: ""
        set(value) = appCtx.putPrefString("homepageSourceHidden", value)

}
