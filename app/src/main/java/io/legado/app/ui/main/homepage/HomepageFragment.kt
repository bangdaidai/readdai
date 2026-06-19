package io.legado.app.ui.main.homepage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.startActivity

class HomepageFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")
    private var composeView: ComposeView? = null

    override fun onResume() {
        super.onResume()
        val activity = activity ?: return
        val primaryColor = activity.primaryColor
        val isTransparent = AppConfig.isTransparentStatusBar
        activity.setStatusBarColorAuto(primaryColor, isTransparent, false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewTreeLifecycleOwner(viewLifecycleOwner)
            composeView = this
            setContent {
                ReaddaiTheme {
                    HomepageScreen(
                    onBookClick = { name, author, bookUrl, origin, coverPath, _ ->
                        startActivity<BookInfoActivity> {
                            putExtra("name", name)
                            putExtra("author", author)
                            putExtra("bookUrl", bookUrl)
                            putExtra("origin", origin)
                            putExtra("coverPath", coverPath)
                        }
                    },
                    onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                        if (!exploreUrl.isNullOrBlank()) {
                            startActivity<ExploreShowActivity> {
                                putExtra("exploreName", title)
                                putExtra("sourceUrl", sourceUrl)
                                putExtra("exploreUrl", exploreUrl)
                            }
                        }
                    },
                )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView?.disposeComposition()
        composeView = null
    }
}
