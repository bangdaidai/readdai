package io.legado.app.ui.main.homepage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.startActivity

class HomepageFragment(override val position: Int?) : Fragment(), MainFragmentInterface {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
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
