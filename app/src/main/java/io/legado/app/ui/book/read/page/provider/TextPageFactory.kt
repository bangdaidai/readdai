package io.legado.app.ui.book.read.page.provider

import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.PageFactory
import io.legado.app.ui.book.read.page.entities.TextPage
import splitties.init.appCtx

class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource) {

    private val keepSwipeTip = appCtx.getString(R.string.keep_swipe_tip)

    override fun hasPrev(): Boolean = with(dataSource) {
        return hasPrevChapter() || pageIndex > 0
    }

    override fun hasNext(): Boolean = with(dataSource) {
        // 原项目逻辑：末章总是返回 true，允许翻到"书末页"
        val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
        if (isLastChapter) return true
        return hasNextChapter() || (currentChapter != null && pageIndex < (currentChapter?.pageSize ?: 1) - 1)
    }

    override fun hasNextPlus(): Boolean = with(dataSource) {
        return hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }

    override fun moveToFirst() {
        ReadBook.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        currentChapter?.let {
            if (it.pageSize == 0) {
                ReadBook.setPageIndex(0)
            } else {
                ReadBook.setPageIndex(it.pageSize.minus(1))
            }
        } ?: ReadBook.setPageIndex(0)
    }

    /**
     * 翻页逻辑，参考原项目设计：
     * 1. hasNext 对末章末页返回 true（允许手势启动）
     * 2. moveToNext 内检测到末章末页 → onBookEnd
     * 3. hasNext 为 false 时兜底也调 onBookEnd（原项目的双重保障）
     */
    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasNext()) {
            val pageIndex = pageIndex
            val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
            if (currentChapter == null || currentChapter?.isLastIndex(pageIndex) == true) {
                if (isLastChapter) {
                    ReadBook.callBack?.onBookEnd()
                    return@with false
                }
                if ((currentChapter == null || isScroll) && nextChapter == null) {
                    return@with false
                }
                ReadBook.moveToNextChapter(upContent, false)
            } else {
                if (pageIndex < 0 || currentChapter?.isLastIndexCurrent(pageIndex) == true) {
                    if (isLastChapter) {
                        ReadBook.callBack?.onBookEnd()
                        return@with false
                    }
                    return@with false
                }
                ReadBook.setPageIndex(pageIndex.plus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else {
            // 兜底：hasNext 为 false（异常场景），仍然触发 onBookEnd
            val pageIndex = pageIndex
            val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
            if (isLastChapter && currentChapter != null && pageIndex >= currentChapter!!.pageSize - 1) {
                ReadBook.callBack?.onBookEnd()
            }
            false
        }
    }

    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasPrev()) {
            if (pageIndex <= 0) {
                if (currentChapter == null && prevChapter == null) {
                    return@with false
                }
                if (prevChapter != null && prevChapter?.isCompleted == false) {
                    return@with false
                }
                ReadBook.moveToPrevChapter(upContent, upContentInPlace = false)
            } else {
                if (currentChapter == null) {
                    return@with false
                }
                ReadBook.setPageIndex(pageIndex.minus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }

    override val curPage: TextPage
        get() = with(dataSource) {
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val page = it.getPage(pageIndex)
                    ?: TextPage(title = it.title).apply { textChapter = it }.format()
                return@with page
            }
            return TextPage().format()
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 1) {
                    return@with it.getPage(pageIndex + 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
            }
            nextChapter?.let {
                return@with it.getPage(0)?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            ReadBook.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex > 0) {
                    return@with it.getPage(pageIndex - 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
            }
            prevChapter?.let {
                return@with it.lastPage?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPlusPage: TextPage
        get() = with(dataSource) {
            currentChapter?.let {
                val pageIndex = pageIndex
                if (pageIndex < it.pageSize - 2) {
                    return@with it.getPage(pageIndex + 2)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                if (!it.isCompleted) {
                    return@with TextPage(title = it.title).format()
                }
                nextChapter?.let { nc ->
                    if (pageIndex < it.pageSize - 1) {
                        return@with nc.getPage(0)?.removePageAloudSpan()
                            ?: TextPage(title = nc.title).format()
                    }
                    return@with nc.getPage(1)?.removePageAloudSpan()
                        ?: TextPage(text = keepSwipeTip).format()
                }
            }
            return TextPage().format()
        }
}
