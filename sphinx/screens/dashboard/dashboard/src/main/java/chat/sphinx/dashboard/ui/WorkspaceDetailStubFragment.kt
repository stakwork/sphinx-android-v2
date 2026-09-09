package chat.sphinx.dashboard.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspaceDetailStubBinding

class WorkspaceDetailStubFragment : Fragment(R.layout.fragment_workspace_detail_stub) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentWorkspaceDetailStubBinding.bind(view)
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        binding.textViewWorkspaceDetailStub.text = getString(
            R.string.workspace_detail_stub_coming_soon,
            title
        )
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String): WorkspaceDetailStubFragment {
            return WorkspaceDetailStubFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}
