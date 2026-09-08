package chat.sphinx.dashboard.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentWorkspaceDetailBinding
import chat.sphinx.insetter_activity.InsetterActivity
import chat.sphinx.insetter_activity.addStatusBarPadding
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import kotlinx.coroutines.launch

@AndroidEntryPoint
internal class WorkspaceDetailFragment : SideEffectFragment<
        Context,
        ChatListSideEffect,
        WorkspaceDetailViewState,
        WorkspaceDetailViewModel,
        FragmentWorkspaceDetailBinding
        >(R.layout.fragment_workspace_detail) {

    override val viewModel: WorkspaceDetailViewModel by viewModels()
    override val binding: FragmentWorkspaceDetailBinding by viewBinding(FragmentWorkspaceDetailBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(requireActivity() as InsetterActivity)
        setupViewPager()
    }

    private fun setupHeader(insetterActivity: InsetterActivity) {
        binding.layoutConstraintWorkspaceDetailHeader.apply {
            insetterActivity.addStatusBarPadding(this)

            layoutParams.height = layoutParams.height + insetterActivity.statusBarInsetHeight.top
            requestLayout()
        }

        binding.textViewWorkspaceDetailName.text = viewModel.workspaceName

        binding.textViewWorkspaceDetailNavBack.setOnClickListener {
            lifecycleScope.launch {
                viewModel.popBackStack()
            }
        }

        binding.imageViewWorkspaceDetailSearch.setOnClickListener {}
        binding.textViewWorkspaceDetailCreateFeature.setOnClickListener {}
    }

    private fun setupViewPager() {
        val adapter = WorkspaceDetailFragmentsAdapter(this)

        binding.viewPagerWorkspaceDetail.apply {
            this.adapter = adapter
            isUserInputEnabled = true
            offscreenPageLimit = 4
            currentItem = WorkspaceDetailFragmentsAdapter.FEATURES_TAB_POSITION
        }

        TabLayoutMediator(
            binding.tabLayoutWorkspaceDetail,
            binding.viewPagerWorkspaceDetail
        ) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }

    override suspend fun onViewStateFlowCollect(viewState: WorkspaceDetailViewState) {}

    override suspend fun onSideEffectCollect(sideEffect: ChatListSideEffect) {
        sideEffect.execute(binding.root.context)
    }
}
