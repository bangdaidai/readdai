package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.LinearLayout
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.widget.text.ScrollTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookTag
import io.legado.app.data.entities.BookTagRelation
import io.legado.app.data.entities.ReadingMemory
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebViewPool
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebJsExtensions.Companion.getInjectionString
import io.legado.app.help.ai.rag.VectorConfigManager
import io.legado.app.help.ai.rag.VectorSearchService
import io.legado.app.help.ai.rag.VectorProgress
import io.legado.app.help.ai.rag.VectorStatus
import io.legado.app.data.appDb
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.CoverColorExtractor
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.help.book.BookHelp
import io.legado.app.model.BookCover
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.readingmemory.ReadingMemoryDetailActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.widget.dialog.BookTagEditDialog
import io.legado.app.ui.widget.dialog.BookTagOptionsDialog
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openFileUri
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookInfoActivity :
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark, showOpenMenuIcon = false),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback {

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        val durChapterIndex = it[0] as Int
                        val durChapterPos = it[1] as Int
                        val durVolumeIndex = it[3] as Int
                        val chapterInVolumeIndex = it[4] as Int
                        book.durChapterIndex = durChapterIndex
                        book.durChapterPos = durChapterPos
                        chapterChanged = it[2] as Boolean
                        book.durVolumeIndex = durVolumeIndex
                        book.chapterInVolumeIndex = chapterInVolumeIndex
                        appDb.bookDao.update(book)
                    }
                    startReadActivity(book)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook() //进目录会保存book，此时退出目录触发的book删除，不通知书源回调
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.upBook(intent)
        when (it.resultCode) {
            RESULT_OK -> {
                viewModel.inBookshelf = true
                upTvBookshelf()
            }

            RESULT_DELETED -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_CANCELED) {
            return@registerForActivityResult
        }
        book?.let { book ->
            viewModel.bookSource = appDb.bookSourceDao.getBookSource(book.origin)?.also { source ->
                viewModel.hasCustomBtn = source.customButton
            }
            viewModel.refreshBook(book)
        }
    }
    private var chapterChanged = false
    private val waitDialog by lazy { WaitDialog(this) }
    private var editMenuItem: MenuItem? = null
    private var menuCustomBtn: MenuItem? = null
    private val book get() = viewModel.getBook(false)

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()
    private var initIntroView = false
    private val introTextView by lazy {
        initIntroView = true
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_book_intro, binding.tvIntroContainer, false) as ScrollTextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            view.revealOnFocusHint = false
        }
        view
    }

    private var pooledWebView: PooledWebView? = null

    private val imgAvailableWidth by lazy {
        val textView = introTextView
        textView.width - textView.paddingLeft - textView.paddingRight - 8.dpToPx()  //8是为了文字对齐额外的右边距
    }
    private var initGetter = false
    private val glideImageGetter by lazy {
        initGetter = true
        GlideImageGetter(
            this,
            introTextView,
            lifecycle,
            imgAvailableWidth,
            viewModel.bookSource?.bookSourceUrl
        )
    }
    private val textViewTagHandler by lazy {
        TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(this@BookInfoActivity, "info button $name" , click)
            }
        })
    }

    @SuppressLint("PrivateResource")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundResource(R.color.transparent)
        binding.refreshLayout?.setColorSchemeColors(accentColor)
        binding.vwBg.applyNavigationBarPadding()
        binding.arcView?.setBgColor(backgroundColor)
        binding.llInfo.background = null
        binding.ivCoverC.setCardBackgroundColor(backgroundColor)
        binding.flAction.setBackgroundColor(bottomBackground)
        (binding.tvShelf as android.widget.TextView).setTextColor(getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground)))
        (binding.tvToc as android.widget.TextView).text = getString(R.string.toc_s, getString(R.string.loading))
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) { upLoading(false, it) }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.abandonedWarningData.observe(this) { warningMessage ->
            // 显示弃文提示
            warningMessage?.let {
                alert("弃文提示", it) {
                    yesButton {}
                }
            }
        }
        binding.titleBar.toolbar.contentInsetEndWithActions = 0
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        editMenuItem = menu.findItem(R.id.menu_edit)
        menuCustomBtn = menu.findItem(R.id.menu_custom_btn).also {
            it.isVisible = viewModel.hasCustomBtn
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt ?: false
        menu.findItem(R.id.menu_upload)?.isVisible =
            viewModel.bookData.value?.isLocal ?: false
        menu.findItem(R.id.menu_delete_alert)?.isChecked =
            LocalConfig.bookInfoDeleteAlert
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_custom_btn -> {
                viewModel.bookSource?.customButton?.let {
                    viewModel.getBook()?.let { book ->
                        SourceCallBack.callBackBtn(this,SourceCallBack.CLICK_CUSTOM_BUTTON, viewModel.bookSource, book, null)
                    }
                }
            }

            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    infoEditResult.launch {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_reading_detail -> {
                viewModel.getBook()?.let {
                    startActivity<ReadingMemoryDetailActivity> {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_share_it -> {
                viewModel.getBook()?.let {
                        val bookJson = GSON.toJson(it)
                        val shareStr = "${it.bookUrl}#$bookJson"
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_SHARE_BOOK,
                        viewModel.bookSource,
                        it,
                        null,
                        result = shareStr
                    ) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra(Intent.EXTRA_TEXT, shareStr)
                        intent.type = "text/plain"
                        startActivity(Intent.createChooser(intent, it.name))
                    }
                }
            }

            R.id.menu_refresh -> {
                refreshBook()
            }

            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                    putExtra("bookUrl", book?.bookUrl)
                }
            }

            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_BOOK_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.bookUrl
                ) {
                    sendToClip(it.bookUrl)
                }
            }

            R.id.menu_copy_toc_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_TOC_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.tocUrl
                ) {
                    sendToClip(it.tocUrl)
                }
            }

            R.id.menu_can_update -> {
                viewModel.getBook()?.let {
                    it.canUpdate = !it.canUpdate
                    if (viewModel.inBookshelf) {
                        if (!it.canUpdate) {
                            it.removeType(BookType.updateError)
                        }
                        viewModel.saveBook(it)
                    }
                }
            }

            R.id.menu_clear_cache -> viewModel.getBook()?.let {
                    SourceCallBack.callBackBtn(this, SourceCallBack.CLICK_CLEAR_CACHE, viewModel.bookSource, it, null) {
                        viewModel.clearCache()
                    }
                }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                viewModel.getBook()?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }

            R.id.menu_delete_alert -> LocalConfig.bookInfoDeleteAlert = !item.isChecked
            R.id.menu_upload -> {
                viewModel.getBook()?.let { book ->
                    book.getRemoteUrl()?.let {
                        alert(R.string.draw, R.string.sure_upload) {
                            okButton {
                                upLoadBook(book)
                            }
                            cancelButton()
                        }
                    } ?: upLoadBook(book)
                }
            }

            R.id.menu_vectorize -> {
                viewModel.getBook()?.let { book ->
                    showVectorizeDialog(book)
                }
            }

            R.id.menu_vector_help -> {
                showVectorHelpDialog()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                }
            }
        }

        // 监听书源变更事件，自动刷新界面
        observeEvent<String>(EventBus.SOURCE_CHANGED) { bookUrl ->
            // 如果是当前书籍的书源变更，则刷新界面
            viewModel.bookData.value?.let { book ->
                if (book.bookUrl == bookUrl) {
                    // 刷新书籍信息显示
                    showBook(book)
                    // 刷新章节列表显示
                    upLoading(false, viewModel.chapterListData.value)
                }
            }
        }

        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshToc()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (initIntroView && ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it === introTextView && introTextView.hasSelection()) {
                    it.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshBook() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun refreshToc() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.loadChapter(it, true, isFromBookInfo = true)
        }
    }

    private fun upLoadBook(
        book: Book,
        bookWebDav: RemoteBookWebDav? = AppWebDav.defaultBookWebDav,
    ) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                bookWebDav
                    ?.upload(book)
                    ?: throw NoStackTraceException("未配置webDav")
                //更新书籍最后更新时间,使之比远程书籍的时间新
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun showBook(book: Book) = binding.run {
        showCover(book)
        (tvName as android.widget.TextView).text = book.name
        (tvAuthor as android.widget.TextView).text = getString(R.string.author_show, book.getRealAuthor())
        (tvOrigin as android.widget.TextView).text = getString(R.string.origin_show, book.originName)
        (tvLasted as android.widget.TextView).text = getString(R.string.lasted_show, book.latestChapterTitle)
        showBookIntro(book)
        if (book.isWebFile) {
            llToc.gone()
            (tvLasted as android.widget.TextView).text = getString(R.string.lasted_show, "下载中...")
        } else {
            llToc.visible()
        }
        menuCustomBtn?.isVisible = viewModel.hasCustomBtn
        editMenuItem?.isVisible = viewModel.inBookshelf
        upTvBookshelf()
        upKinds(book)
        upGroup(book.group)
    }

    inner class CustomWebViewClient : WebViewClient() {
        private val jsStr = getInjectionString
        override fun shouldOverrideUrlLoading(
            view: android.webkit.WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.let {
                val uri = it.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "legado", "yuedu" -> {
                        startActivity<OnLineImportActivity> {
                            data = uri
                        }
                        true
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            openUrl(uri)
                        }
                        true
                    }
                }
            }
            return true
        }
        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.evaluateJavascript(jsStr, null)
        }
        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.post {
                binding.tvIntroContainer.requestLayout()
            }
        }
    }
    private fun showBookIntro(book: Book) {
        val intro = book.getDisplayIntro()
        if (intro?.startsWith("<useweb>") == true) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 8) {
                introTextView.text = intro
                return
            }
            val html = intro.substring(8, lastIndex)
            val pooledWebView = this.pooledWebView ?: let{
                val pooledWebView = WebViewPool.acquire(this)
                val webView = pooledWebView.realWebView
                webView.onResume()
                webView.webViewClient = object: WebViewClient() {
                    private val jsStr = getInjectionString
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.let {
                            val uri = it.url
                            return when (uri.scheme) {
                                "http", "https" -> false
                                "legado", "yuedu" -> {
                                    startActivity<OnLineImportActivity> {
                                        data = uri
                                    }
                                    true
                                }

                                else -> {
                                    binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                                        openUrl(uri)
                                    }
                                    true
                                }
                            }
                        }
                        return true
                    }
                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(jsStr, null)
                    }
                }
                viewModel.bookSource?.let {
                    webView.addJavascriptInterface(it as BaseSource, nameSource)
                    val webJsExtensions = WebJsExtensions(it, null, webView)
                    webView.addJavascriptInterface(webJsExtensions, nameJava)
                }
                pooledWebView
            }
            val webView = pooledWebView.realWebView
            if (initIntroView || this.pooledWebView == null) {
                initIntroView = false
                this.pooledWebView = pooledWebView
                binding.tvIntroContainer.removeAllViews()
                binding.tvIntroContainer.addView(webView)
            }
            val bookUrl = viewModel.getBook()?.bookUrl?.substringBefore(",")
            webView.loadDataWithBaseURL(bookUrl, html, "text/html", "utf-8", bookUrl)
            return
        }
        if (!initIntroView || pooledWebView != null) {
            destroyWeb()
            binding.tvIntroContainer.removeAllViews()
            binding.tvIntroContainer.addView(introTextView)
        }
        if (intro.isNullOrBlank()) {
            return
        }
        val tvIntro = introTextView
        if (intro.startsWith("<usehtml>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 9) {
                tvIntro.text = intro
                return
            }
            val html = intro.substring(9, lastIndex)
            tvIntro.setHtml(
                html,
                glideImageGetter,
                textViewTagHandler,
                imgOnLongClickListener = {
                    showDialogFragment(PhotoDialog(it, viewModel.bookSource?.bookSourceUrl))
                },
                imgOnClickListener = {
                    viewModel.onButtonClick(this@BookInfoActivity, "info image" , it)
                }
            )
        } else if (intro.startsWith("<md>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 4) {
                tvIntro.text = intro
                return
            }
            val mark = intro.substring(4, lastIndex)
            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tvIntro.setTextClassifier(TextClassifier.NO_OP)
                }
                val context = this@BookInfoActivity
                val markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(context)
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(context)
                                    .applyDefaultRequestOptions(
                                        RequestOptions()
                                            .override(imgAvailableWidth)
                                            .encodeQuality(88)
                                    )
                            )
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(context))
                        .build()
                    markwon.toMarkdown(mark)
                }
                tvIntro.setMarkdown(
                    markwon,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source, viewModel.bookSource?.bookSourceUrl))
                    }
                )
            }
        } else {
            tvIntro.text = intro
        }
    }

    private fun upKinds(book: Book) = binding.run {
        lifecycleScope.launch {
            var kinds = book.getKindList()
            if (book.isLocal) {
                withContext(IO) {
                    val size = FileDoc.fromFile(book.bookUrl).size
                    if (size > 0) {
                        kinds = kinds.toMutableList()
                        kinds.add(ConvertUtils.formatFileSize(size))
                    }
                }
            }
            if (kinds.isEmpty()) {
                lbKind.gone()
            } else {
                lbKind.visible()
                val source = viewModel.bookSource
                if (source == null) {
                    lbKind.setLabels(kinds)
                    return@launch
                }
                lbKind.setLabels(
                    kinds,
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind
                        ) {
                            SearchActivity.start(this@BookInfoActivity, source, kind)
                        }
                    },
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.LONG_CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind
                        )
                        true
                    }
                )
            }
        }
    }

    private fun showCover(book: Book) {
        val coverPath = book.getDisplayCover()
        binding.ivCover.load(book, false) {
            if (!AppConfig.isEInkMode) {
                BookCover.loadBlur(this, coverPath, false, book.origin)
                    .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: Target<android.graphics.drawable.Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            if (!AppConfig.isEInkMode && !coverPath.isNullOrBlank()) {
                                extractCoverColor(coverPath)
                            }
                            return false
                        }
                    })
                    .into(binding.bgBook)
            }
        }
    }

    private fun extractCoverColor(coverPath: String) {
        ImageLoader.load(this, coverPath)
            .override(128, 128)
            .into(object : CustomTarget<android.graphics.drawable.Drawable>() {
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    transition: Transition<in android.graphics.drawable.Drawable>?
                ) {
                    if (resource is android.graphics.drawable.BitmapDrawable) {
                        val dominantColor = CoverColorExtractor.extractDominantColor(resource.bitmap)
                        updateGradientBackground(dominantColor)
                    }
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    updateGradientBackground(Color.GRAY)
                }
            })
    }

    private fun updateGradientBackground(dominantColor: Int) {
        val darkenedColor = CoverColorExtractor.darkenColor(dominantColor, 0.6f)
        val lightenedColor = CoverColorExtractor.lightenColor(dominantColor, 0.3f)

        binding.gradientCover?.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(80, Color.red(darkenedColor), Color.green(darkenedColor), Color.blue(darkenedColor)),
                Color.argb(160, Color.red(darkenedColor), Color.green(darkenedColor), Color.blue(darkenedColor))
            )
        )

        val arcColor = Color.argb(230, Color.red(lightenedColor), Color.green(lightenedColor), Color.blue(lightenedColor))
        binding.arcView?.setBgColor(arcColor)

        binding.llInfo.setBackgroundColor(
            Color.argb(180, Color.red(darkenedColor), Color.green(darkenedColor), Color.blue(darkenedColor))
        )
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                (binding.tvToc as android.widget.TextView).text = getString(R.string.toc_s, getString(R.string.loading))
            }

            chapterList.isNullOrEmpty() -> {
                (binding.tvToc as android.widget.TextView).text = getString(
                    R.string.toc_s,
                    getString(R.string.error_load_toc)
                )
                (binding.tvLasted as android.widget.TextView).text = getString(R.string.lasted_show, book?.latestChapterTitle)
            }

            else -> {
                book?.let {
                    (binding.tvToc as android.widget.TextView).text = getString(R.string.toc_s, it.durChapterTitle)
                    (binding.tvLasted as android.widget.TextView).text = getString(R.string.lasted_show, it.latestChapterTitle)
                }
            }
        }
    }

    private fun upTvBookshelf() {
        if (viewModel.inBookshelf) {
            (binding.tvShelf as android.widget.TextView).text = getString(R.string.remove_from_bookshelf)
        } else {
            (binding.tvShelf as android.widget.TextView).text = getString(R.string.add_to_bookshelf)
        }
        (binding.tvShelf as android.widget.TextView).setTextColor(accentColor)
        editMenuItem?.isVisible = viewModel.inBookshelf
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            if (it.isNullOrEmpty()) {
                (binding.tvGroup as android.widget.TextView).text = if (book?.isLocal == true) {
                    getString(R.string.group_s, getString(R.string.local_no_group))
                } else {
                    getString(R.string.group_s, getString(R.string.no_group))
                }
            } else {
                (binding.tvGroup as android.widget.TextView).text = getString(R.string.group_s, it)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {
                    upTvBookshelf()
                }
            }
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            showCover(book)
            lifecycleScope.launch(IO) {
                io.legado.app.help.book.BookInfoSyncHelper.updateBookCover(book.bookUrl, coverUrl)
            }
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        ivCover.setOnLongClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path, isBook = true))
            }
            true
        }
        tvRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isWebFile) {
                    showWebFileDownloadAlert {
                        readBook(it)
                    }
                } else {
                    readBook(book)
                }
            }
        }
        tvShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf {
                            upTvBookshelf()
                        }
                    }
                }
            }
        }
        tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        tvChangeSource.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author, book.type))
            }
        }
        tvTocView.setOnClickListener {
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) { //点击目录会保存book
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        tvChangeGroup.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            }
        }
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
        }
        tvAuthor.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
            true
        }
        tvName.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
        }
        tvName.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
            true
        }
        refreshLayout?.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            refreshBook()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let { book ->
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del
                ) {
                    var checkBox: CheckBox? = null
                    if (book.isLocal) {
                        checkBox = CheckBox(this@BookInfoActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }

                    // 否按钮 - 取消删除
                    negativeButton(R.string.no) {}

                    // 是按钮 - 删除书籍但不标记为弃文
                    neutralButton(R.string.yes) {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book)
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }

                    // 是并弃文按钮 - 删除书籍并标记为弃文
                    positiveButton("是并弃文") {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book)
                        viewModel.delBookAndMarkAsAbandoned(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
            } else {
                SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book) //点按钮直接删除书架
                // 默认保留阅读记忆，不标记为弃文
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    // 移除showWebFileDownloadAlert方法，因为它依赖于已移除的webFiles相关功能
    // 该方法用于处理Web文件下载，但相关功能已经不再使用
    @Suppress("unused")
    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(
            R.string.download_and_import_file,
            webFiles
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    // 移除showDecompressFileImportAlert方法，因为它依赖于已移除的webFiles相关功能
    // 该方法用于处理压缩文件导入，但相关功能已经不再使用
    @Suppress("unused")
    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
    }

    private fun readBook(book: Book) {
        // 检查书籍是否在书架上
        if (viewModel.inBookshelf) {
            // 如果书籍已经在书架上，直接开始阅读，不显示阅读记录提示
            lifecycleScope.launch {
                runOnUiThread {
                    continueReadBook(book, null)
                }
            }
        } else {
            // 如果书籍不在书架上（删除后重新阅读），检查是否存在阅读记录
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    // 查找该书的所有阅读记录
                    val memories = appDb.readingMemoryDao.getByBook(book.name, book.author)

                    if (memories.isNotEmpty()) {
                        // 获取最新的阅读记录
                        val latestMemory = memories.maxByOrNull { it.updateTime } ?: memories[0]

                        // 检查是否有有效的阅读进度
                        val hasValidProgress = latestMemory.progress > 0f || latestMemory.durChapterIndex > 0 || latestMemory.durChapterPos > 0

                        // 检查是否为弃文记录
                        val isAbandoned = latestMemory.readingStatus == io.legado.app.constant.ReadingStatus.ABANDONED

                        // 显示统一的阅读记录提示，包括弃文状态
                        runOnUiThread {
                            showReadingRecordAlert(book, latestMemory, hasValidProgress, isAbandoned)
                        }
                    } else {
                        // 没有阅读记录，直接开始阅读
                        runOnUiThread {
                            continueReadBook(book, null)
                        }
                    }
                }
            }
        }
    }

    /**
     * 显示统一的阅读记录提示，包括弃文状态
     */
    private fun showReadingRecordAlert(book: Book, memory: ReadingMemory, hasValidProgress: Boolean, isAbandoned: Boolean) {
        // 构建提示消息
        val messageBuilder = StringBuilder()

        // 显示最后阅读时间
        val readDate = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(memory.updateTime),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))

        if (hasValidProgress) {
            // 显示阅读进度
            val progressPercent = memory.progress.toInt()
            val chapterInfo = "第${memory.durChapterIndex + 1}章 ${memory.durChapterTitle}"
            messageBuilder.append("您最后于${readDate}阅读到 ${progressPercent}%，${chapterInfo}")
        } else {
            // 没有有效进度，只显示最后阅读时间
            messageBuilder.append("您最后于${readDate}阅读过该书")
        }

        // 显示弃文状态
        if (isAbandoned) {
            messageBuilder.append("，已弃文")
        }

        messageBuilder.append("\n是否从此处继续？")

        // 显示提示对话框
        alert("阅读记录", messageBuilder.toString()) {
            // 继续阅读
            yesButton {
                continueReadBook(book, memory)
            }
            // 从头开始
            noButton {
                continueReadBook(book, null)
            }
        }
    }

    /**
     * 检查是否存在历史阅读记录
     */
    private fun checkReadingHistory(book: Book) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // 查找该书的所有阅读记录
                val memories = appDb.readingMemoryDao.getByBook(book.name, book.author)

                if (memories.isNotEmpty()) {
                    // 获取最新的阅读记录
                    val latestMemory = memories.maxByOrNull { it.updateTime } ?: memories[0]

                    // 检查是否有有效的阅读进度
                    val hasValidProgress = latestMemory.progress > 0f || latestMemory.durChapterIndex > 0 || latestMemory.durChapterPos > 0

                    // 检查是否为弃文记录
                    val isAbandoned = latestMemory.readingStatus == io.legado.app.constant.ReadingStatus.ABANDONED

                    // 显示统一的阅读记录提示
                    runOnUiThread {
                        showReadingRecordAlert(book, latestMemory, hasValidProgress, isAbandoned)
                    }
                } else {
                    // 没有阅读记录，直接开始阅读
                    runOnUiThread {
                        continueReadBook(book, null)
                    }
                }
            }
        }
    }

    /**
     * 继续阅读书籍
     * @param book 书籍对象
     * @param memory 阅读记录对象，如果为null则从0开始阅读
     */
    private fun continueReadBook(book: Book, memory: ReadingMemory?) {
        // 如果有阅读记录，使用其进度
        if (memory != null) {
            // 更新书籍的阅读进度
            book.durChapterIndex = memory.durChapterIndex
            book.durChapterPos = memory.durChapterPos
            book.durChapterTitle = memory.durChapterTitle
            // 保存更新后的书籍信息
            viewModel.saveBook(book)
        }

        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        val showText = "Loading....."
        if (isShow) {
            waitDialog.run {
                setText(showText)
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )
            book.isVideo -> readBookResult.launch(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )

            else -> readBookResult.launch(
                Intent(
                    this,
                    if (!book.isLocal && book.isImage && AppConfig.showMangaUi) ReadMangaActivity::class.java
                    else ReadBookActivity::class.java
                )
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("chapterChanged", chapterChanged)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (initGetter) {
            glideImageGetter.start()
        }
    }

    override fun onStop() {
        super.onStop()
        if (initGetter) {
            glideImageGetter.stop()
        }
    }

    override fun onDestroy() {
        destroyWeb()
        super.onDestroy()
        if (initGetter) {
            glideImageGetter.clear()
        }
    }

    private fun destroyWeb() {
        pooledWebView?.let { WebViewPool.release(it) }
        pooledWebView = null
    }

    /**
     * 显示向量化对话框
     */
    private fun showVectorizeDialog(book: Book) {
        val config = VectorConfigManager.getConfig()

        // 检查是否启用且配置完整
        if (!config.enabled || config.apiKey.isBlank() || config.modelName.isBlank()) {
            alert("向量模型未配置", "请先在AI设置 → 向量模型中配置向量模型") {
                yesButton {
                    startActivity(Intent(this@BookInfoActivity, io.legado.app.ui.config.ConfigActivity::class.java).apply {
                        putExtra("configTag", io.legado.app.ui.config.ConfigTag.AI_SETTINGS)
                    })
                }
                noButton { }
            }
            return
        }

        lifecycleScope.launch {
            waitDialog.show()
            waitDialog.setText("正在获取章节...")

            try {
                val chapters = withContext(Dispatchers.IO) {
                    appDb.bookChapterDao.getChapterList(book.bookUrl)
                        .map { chapter ->
                            val content = withContext(Dispatchers.IO) {
                                BookHelp.getContent(book, chapter)?.take(50000)
                            } ?: ""
                            chapter.index to content
                        }
                }

                if (chapters.isEmpty()) {
                    waitDialog.dismiss()
                    toastOnUi("无法获取书籍章节内容")
                    return@launch
                }
                
                // 检查是否有实际内容
                val chaptersWithContent = chapters.filter { it.second.isNotBlank() }
                
                if (chaptersWithContent.isEmpty()) {
                    waitDialog.dismiss()
                    toastOnUi("所有章节内容为空，请先阅读章节以缓存内容")
                    return@launch
                }

                val vectorService = VectorSearchService(this@BookInfoActivity)

                withContext(Dispatchers.Main) {
                    waitDialog.dismiss()
                    alert("开始向量化", "将对《${book.name}》进行向量化，预计需要几分钟时间，是否继续？") {
                        yesButton {
                            performVectorization(book, chapters, vectorService)
                        }
                        noButton { }
                    }
                }
            } catch (e: Exception) {
                waitDialog.dismiss()
                toastOnUi("获取章节失败: ${e.message}")
            }
        }
    }

    /**
     * 执行向量化
     */
    private fun performVectorization(
        book: Book,
        chapters: List<Pair<Int, String>>,
        vectorService: VectorSearchService
    ) {
        lifecycleScope.launch {
            val config = VectorConfigManager.getConfig()
            
            // 显示进度对话框
            waitDialog.show()
            waitDialog.setText("正在向量化...")

            try {
                val result = vectorService.vectorizeBook(
                    bookUrl = book.bookUrl,
                    bookTitle = book.name,
                    chapters = chapters,
                    config = config
                ) { progress ->
                    // 更新进度（在主线程上更新UI）
                    when (progress.status) {
                        io.legado.app.help.ai.rag.VectorStatus.PROCESSING -> {
                            runOnUiThread {
                                waitDialog.setText("正在向量化... ${progress.processedChunks}/${progress.totalChunks}")
                            }
                        }
                        else -> {}
                    }
                }
                
                withContext(Dispatchers.Main) {
                    waitDialog.dismiss()
                    if (result.isSuccess) {
                        toastOnUi("向量化完成！")
                    } else {
                        toastOnUi("向量化失败: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    waitDialog.dismiss()
                    toastOnUi("向量化异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 显示向量模型使用帮助
     */
    private fun showVectorHelpDialog() {
        val helpText = """
            📚 向量模型使用指南

            一、什么是向量化？
            向量化是将书籍内容转换为"向量"（数字数组）的过程，让AI能够理解内容的语义。

            二、为什么要向量化？
            向量化后，你可以：
            • 使用语义搜索 - 问"书里讲了什么关于自由的内容"，AI会理解你的意图
            • 精准定位 - 快速找到相关段落
            • 智能摘要 - AI能更好地总结全书内容

            三、使用步骤

            1️⃣ 配置向量模型
            AI设置 → 向量模型Tab
            - 选择提供商（OpenAI/SiliconFlow/阿里云等）
            - 选择嵌入模型（推荐 text-embedding-3-small）
            - 填入API Key
            - 点击"测试连接"确保配置正确
            - 点击"保存配置"

            2️⃣ 向量化书籍
            在书籍详情页：
            - 点击菜单 → "向量化书籍"
            - 等待几分钟（根据书籍长度）
            - 向量化完成后会提示

            3️⃣ 使用RAG搜索
            向量化完成后，在AI对话中：
            - 直接问关于书籍内容的问题
            - AI会自动使用RAG搜索相关内容

            四、支持的提供商
            • OpenAI - 需要API Key，收费但效果好
            • SiliconFlow - 国内可用，便宜
            • 阿里云 - 需要阿里云API Key
            • DeepSeek - 便宜效果好
            • Ollama - 本地部署，完全免费

            五、注意事项
            • 向量化需要网络连接
            • 大量书籍向量化可能消耗API配额
            • 可以删除向量数据释放存储空间
        """.trimIndent()

        alert("向量模型使用说明", helpText) {
            yesButton { }
        }
    }

}