package chat.sphinx.threads.viewstate

import chat.sphinx.threads.model.ThreadItemViewState
import io.matthewnelson.concept_views.viewstate.ViewState

internal sealed class ThreadsViewState: ViewState<ThreadsViewState>() {
    object Idle: ThreadsViewState()
    object NoThreadsFound: ThreadsViewState()
    class ThreadList(val threads: List<ThreadItemViewState>): ThreadsViewState()
}
