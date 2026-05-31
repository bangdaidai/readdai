package io.legado.app.ui.book.read.page.provider

import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.PageFactory
import io.legado.app.ui.book.read.page.entities.TextPage
import splitties.init.appCtx

class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource) {

    private val keepSwipeTip = appCtx.getString(R.string.keep_swipe_tip)
    
    /**
     * 检查是否需要显示藏书票
     */
    private fun checkBookplate(page: TextPage, chapter: io.legado.app.ui.book.read.page.entities.TextChapter) {
        val book = ReadBook.book ?: return
        val isLastChapter = chapter.position >= ReadBook.simulatedChapterSize - 1
        val isFirstPage = chapter.position == 0 && page.index == 0
        val isLastPage = isLastChapter && chapter.isLastIndex(page.index)
        
        // 尾页显示藏书票（最后一章的最后一页）
        if (isLastChapter && isLastPage) {
            page.isBookplateEnd = true
        }
    }

    override fun hasPrev(): Boolean = with(dataSource) {
        return hasPrevChapter() || pageIndex > 0
    }

    override fun hasNext(): Boolean = with(dataSource) {
        return hasNextChapter() || (currentChapter != null && currentChapter?.isLastIndex(pageIndex) != true)
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

    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        // 初始化 showBookplate 标志
        if (ReadBook.showBookplate == -1) {
            ReadBook.showBookplate = 0
            if (upContent) upContent(resetPageOffset = false)
            return@with true
        }
        return if (hasNext()) {
            val pageIndex = pageIndex
            if (currentChapter == null || currentChapter?.isLastIndex(pageIndex) == true) {
                val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
                if (isLastChapter && ReadBook.showBookplate == 0) {
                    val book = ReadBook.book
                    // 如果正在N刷中（readIteration为偶数），弹出完读确认
                    if (book != null && book.readIteration % 2 == 0) {
                        ReadBook.callBack?.onBookEnd()
                        return@with false
                    }
                    // 自动记录完读时间
                    book?.let {
                        if (it.finishReadTime <= 0) {
                            it.finishReadTime = System.currentTimeMillis()
                            io.legado.app.data.appDb.bookDao.update(it)
                        }
                    }
                    ReadBook.showBookplate = 1
                    if (upContent) upContent(resetPageOffset = false)
                    return@with true
                }
                if ((currentChapter == null || isScroll) && nextChapter == null) {
                    return@with false
                }
                ReadBook.moveToNextChapter(upContent, false)
            } else {
                if (pageIndex < 0 || currentChapter?.isLastIndexCurrent(pageIndex) == true) {
                    val isLastChapter = currentChapter?.chapter?.index == (currentChapter?.chaptersSize?.minus(1) ?: 0)
                    if (isLastChapter && ReadBook.showBookplate == 0) {
                        val book = ReadBook.book
                        // 如果正在N刷中（readIteration为偶数），弹出完读确认
                        if (book != null && book.readIteration % 2 == 0) {
                            ReadBook.callBack?.onBookEnd()
                            return@with false
                        }
                        // 自动记录完读时间
                        book?.let {
                            if (it.finishReadTime <= 0) {
                                it.finishReadTime = System.currentTimeMillis()
                                io.legado.app.data.appDb.bookDao.update(it)
                            }
                        }
                        ReadBook.showBookplate = 1
                        if (upContent) upContent(resetPageOffset = false)
                        return@with true
                    }
                    return@with false
                }
                ReadBook.setPageIndex(pageIndex.plus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
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
                
                // 检查是否需要显示藏书票
                checkBookplate(page, it)
                
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
