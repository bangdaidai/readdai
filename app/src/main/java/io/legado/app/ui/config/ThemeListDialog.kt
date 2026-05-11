package io.legado.app.ui.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.getClipText
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class ThemeListDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }

    private val selectBgImage = registerForActivityResult(HandleFileContract()) { result ->
        val index = pendingThemeIndex
        pendingThemeIndex = -1
        val uri = result.uri ?: return@registerForActivityResult
        if (index in ThemeConfig.configList.indices) {
            val config = ThemeConfig.configList[index]
            val path = saveBgImage(uri, config.isNightTheme)
            if (path != null) {
                val updatedConfig = config.copy(
                    backgroundImgPath = path,
                    isNightTheme = config.isNightTheme
                )
                ThemeConfig.configList[index] = updatedConfig
                ThemeConfig.save()
                adapter.notifyItemChanged(index)
                requireContext().toastOnUi(R.string.success)
            }
        }
    }

    private var pendingThemeIndex = -1

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.9f).toInt())
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.theme_list)
        binding.root.setBackgroundColor(ThemeStore.backgroundColor(requireContext()))
        binding.recyclerView.setBackgroundColor(ThemeStore.backgroundColor(requireContext()))
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
        val titleBarTextIconColor = ThemeStore.titleBarTextIconColor(requireContext())
        toolBar.setTitleTextColor(titleBarTextIconColor)
        toolBar.setSubtitleTextColor(titleBarTextIconColor)
        for (i in 0 until toolBar.menu.size()) {
            val item = toolBar.menu.getItem(i)
            item.icon?.setTint(titleBarTextIconColor)
            val title = item.title.toString()
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(titleBarTextIconColor),
                0, title.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            item.title = spannable
        }
    }

    fun initData() {
        adapter.setItems(ThemeConfig.configList)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                requireContext().getClipText()?.let {
                    if (ThemeConfig.addConfig(it)) {
                        initData()
                    } else {
                        toastOnUi("格式不对,添加失败")
                    }
                }
            }
        }
        return true
    }

    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                ThemeConfig.delConfig(index)
                initData()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val json = GSON.toJson(ThemeConfig.configList[index])
        requireContext().share(json, "主题分享")
    }

    private fun selectBackgroundImage(index: Int) {
        pendingThemeIndex = index
        selectBgImage.launch {
            mode = HandleFileContract.IMAGE
            requestCode = 100
        }
    }

    private fun saveBgImage(uri: Uri, isNightTheme: Boolean): String? {
        return try {
            val context = requireContext()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val fileName = "bg_${System.currentTimeMillis()}.png"
            val dir = File(context.getExternalFilesDir(null), "backgrounds")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)

            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            file.absolutePath
        } catch (e: Exception) {
            toastOnUi(e.localizedMessage)
            null
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<ThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ThemeConfig.Config,
            payloads: MutableList<Any>
        ) {
            bindPreview(binding, item)
            
            binding.apply {
                tvName.text = item.themeName

                val infoBuilder = StringBuilder()
                infoBuilder.append(item.primaryColor)
                if (!item.backgroundImgPath.isNullOrBlank()) {
                    infoBuilder.append(" | ").append(context.getString(R.string.background_image))
                }
                tvInfo.text = infoBuilder.toString()

                val textColor = ThemeStore.textColorOther(requireContext())
                tvName.setTextColor(textColor)
                tvInfo.setTextColor(textColor)

                btnApply.setOnClickListener {
                    ThemeConfig.applyConfig(context, ThemeConfig.configList[holder.layoutPosition])
                    context.toastOnUi(R.string.success)
                }

                btnShare.setOnClickListener {
                    share(holder.layoutPosition)
                }

                btnDelete.setOnClickListener {
                    delete(holder.layoutPosition)
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.root.setOnClickListener {
                ThemeConfig.applyConfig(context, ThemeConfig.configList[holder.layoutPosition])
                context.toastOnUi(R.string.success)
            }
        }

        private fun bindPreview(binding: ItemThemeConfigBinding, item: ThemeConfig.Config) {
            val bgDrawable = binding.ivDefaultColor
            val previewDrawable = binding.ivPreview

            bgDrawable.setImageDrawable(null)
            previewDrawable.setImageDrawable(null)

            val primary = runCatching {
                Color.parseColor(item.primaryColor)
            }.getOrDefault(ContextCompat.getColor(context, R.color.md_brown_500))

            val bgColor = runCatching {
                Color.parseColor(item.backgroundColor)
            }.getOrDefault(Color.WHITE)

            val bitmap = createColorPreview(120, 168, primary, bgColor)
            bgDrawable.setImageBitmap(bitmap)

            val bgPath = item.backgroundImgPath
            if (!bgPath.isNullOrBlank()) {
                val bgFile = File(bgPath)
                if (bgFile.exists()) {
                    val bgBitmap = android.graphics.BitmapFactory.decodeFile(bgPath)
                    if (bgBitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(bgBitmap, 120, 168, true)
                        previewDrawable.setImageBitmap(scaledBitmap)
                        previewDrawable.scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
            }
        }

        private fun createColorPreview(width: Int, height: Int, primary: Int, background: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            bgPaint.color = background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val primaryHeight = height * 0.15f
            val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            primaryPaint.color = primary
            canvas.drawRect(0f, 0f, width.toFloat(), primaryHeight, primaryPaint)

            val bottomHeight = height * 0.2f
            val bottomTop = height - bottomHeight
            val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            bottomPaint.color = adjustBrightness(background, -20)
            canvas.drawRect(0f, bottomTop, width.toFloat(), height.toFloat(), bottomPaint)

            return bitmap
        }

        private fun adjustBrightness(color: Int, amount: Int): Int {
            val r = (Color.red(color) + amount).coerceIn(0, 255)
            val g = (Color.green(color) + amount).coerceIn(0, 255)
            val b = (Color.blue(color) + amount).coerceIn(0, 255)
            return Color.argb(Color.alpha(color), r, g, b)
        }
    }
}