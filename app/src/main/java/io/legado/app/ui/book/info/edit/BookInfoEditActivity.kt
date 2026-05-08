package io.legado.app.ui.book.info.edit

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityBookInfoEditBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.removeType
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.readUri
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import splitties.views.bottomPadding
import java.io.FileOutputStream

class BookInfoEditActivity :
    VMBaseActivity<ActivityBookInfoEditBinding, BookInfoEditViewModel>(),
    ChangeCoverDialog.CallBack {

    private val selectCover = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            coverChangeTo(uri)
        }
    }

    override val binding by viewBinding(ActivityBookInfoEditBinding::inflate)
    override val viewModel by viewModels<BookInfoEditViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.bookData.observe(this) { upView(it) }
        if (viewModel.bookData.value == null) {
            intent.getStringExtra("bookUrl")?.let {
                viewModel.loadBook(it)
            }
        }
        initView()
        initEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveData()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            view.bottomPadding = insets.bottom
            windowInsets
        }
    }

    private fun initEvent() = binding.run {
        tvChangeCover.setOnClickListener {
            viewModel.bookData.value?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        tvSelectCover.setOnClickListener {
            selectCover.launch {
                mode = HandleFileContract.IMAGE
            }
        }
        tvRefreshCover.setOnClickListener {
            viewModel.book?.customCoverUrl = tieCoverUrl.text?.toString()
            upCover()
        }
    }

    private fun upView(book: Book) = binding.run {
        tieBookName.setText(book.name)
        tieBookAuthor.setText(book.author)
        spType.setSelection(
            when {
                book.isVideo -> 4
                book.isImage -> 2
                book.isAudio -> 1
                else -> 0
            }
        )
        tieCoverUrl.setText(book.getDisplayCover())
        tieBookIntro.setText(book.getDisplayIntro())
        upCover()
    }

    private fun upCover() {
        viewModel.book?.let {
            binding.ivCover.load(it, false)
        }
    }

    private fun saveData() = binding.run {
        val book = viewModel.book ?: return@run
        val oldBook = book.copy()

        book.name = tieBookName.text?.toString() ?: ""
        book.author = tieBookAuthor.text?.toString() ?: ""

        val local = if (book.isLocal) BookType.local else 0
        val bookType = when (spType.selectedItemPosition) {
            4 -> BookType.video or local
            2 -> BookType.image or local
            1 -> BookType.audio or local
            else -> BookType.text or local
        }

        book.removeType(BookType.video, BookType.local, BookType.image, BookType.audio, BookType.text)
        book.addType(bookType)

        val customCoverUrl = tieCoverUrl.text?.toString()
        book.customCoverUrl = if (customCoverUrl == book.coverUrl) null else customCoverUrl

        val customIntro = tieBookIntro.text?.toString()
        val finalIntro = if (customIntro == book.intro) null else customIntro

        // 更新book对象的customIntro字段
        book.customIntro = finalIntro
        book.userModifiedIntro = finalIntro != null

        // ✅ 修复：使用 lifecycleScope + Dispatchers.IO 调用 suspend 函数
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 挂起函数：必须在协程中调用
                BookHelp.updateCacheFolder(oldBook, book)
                
                // 统一更新书籍名称和作者
                if (oldBook.name != book.name || oldBook.author != book.author) {
                    io.legado.app.help.book.BookInfoSyncHelper.updateBookNameAndAuthor(
                        book.bookUrl, 
                        oldBook.name, 
                        oldBook.author, 
                        book.name, 
                        book.author
                    )
                }
                
                // 使用统一的方法更新简介，确保同步到所有相关记录
                io.legado.app.help.book.BookInfoSyncHelper.updateBookIntro(book.bookUrl, finalIntro)

                // 切回主线程更新 UI / 保存
                withContext(Dispatchers.Main) {
                    viewModel.saveBook(book) {
                        // 书架通过 Flow 监听 Book 表变化，会自动刷新
                        // 无需手动发送事件
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appCtx.toastOnUi("保存失败: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.book?.customCoverUrl = coverUrl
        binding.tieCoverUrl.setText(coverUrl)
        upCover()
    }

    private fun coverChangeTo(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            coverChangeTo(uri.toString())
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                inputStream.use {
                    var file = this.externalFiles
                    val suffix = if (fileDoc.name.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        "." + fileDoc.name.substringAfterLast(".")
                    }
                    val fileName = uri.inputStream(this).getOrThrow().use {
                        MD5Utils.md5Encode(it) + suffix
                    }
                    file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    coverChangeTo(file.absolutePath)
                }
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}