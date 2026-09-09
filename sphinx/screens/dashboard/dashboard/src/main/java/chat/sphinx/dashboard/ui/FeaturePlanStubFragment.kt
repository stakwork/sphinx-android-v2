package chat.sphinx.dashboard.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.databinding.FragmentFeaturePlanStubBinding

class FeaturePlanStubFragment : Fragment(R.layout.fragment_feature_plan_stub) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentFeaturePlanStubBinding.bind(view)
        val featureId = arguments?.getString(ARG_FEATURE_ID).orEmpty()
        val featureTitle = arguments?.getString(ARG_FEATURE_TITLE).orEmpty()
        binding.textViewFeaturePlanStub.text = getString(
            R.string.feature_plan_stub_label,
            featureTitle,
            featureId
        )
    }

    companion object {
        const val ARG_FEATURE_ID = "argFeatureId"
        const val ARG_FEATURE_TITLE = "argFeatureTitle"
    }
}
