package chat.sphinx.edit_contact.ui

import android.icu.util.TimeZone
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.contact.databinding.LayoutContactBinding
import chat.sphinx.contact.databinding.LayoutContactDetailScreenHeaderBinding
import chat.sphinx.contact.databinding.LayoutContactSaveBinding
import chat.sphinx.contact.ui.ContactFragment
import chat.sphinx.detail_resources.databinding.ShareTimezoneLayoutBinding
import chat.sphinx.edit_contact.R
import chat.sphinx.edit_contact.databinding.FragmentEditContactBinding
import chat.sphinx.resources.R.color
import chat.sphinx.resources.setTextColorExt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
internal class EditContactFragment : ContactFragment<
        FragmentEditContactBinding,
        EditContactFragmentArgs,
        EditContactViewModel
        >(R.layout.fragment_edit_contact) {

    @Inject
    lateinit var userColorsHelperInj: UserColorsHelper

    override val userColorsHelper: UserColorsHelper
        get() = userColorsHelperInj

    override val viewModel: EditContactViewModel by viewModels()

    override val binding: FragmentEditContactBinding by viewBinding(FragmentEditContactBinding::bind)

    override val headerBinding: LayoutContactDetailScreenHeaderBinding by viewBinding(
        LayoutContactDetailScreenHeaderBinding::bind, R.id.include_edit_contact_header
    )
    override val contactBinding: LayoutContactBinding by viewBinding(
        LayoutContactBinding::bind, R.id.include_edit_contact_layout
    )

    override val contactSaveBinding: LayoutContactSaveBinding by viewBinding(
        LayoutContactSaveBinding::bind, R.id.include_edit_contact_layout_save
    )

    private val fragmentShareTimezone: ShareTimezoneLayoutBinding by viewBinding(
        ShareTimezoneLayoutBinding::bind, R.id.include_share_timezone_layout
    )

    override fun getHeaderText(): String = getString(R.string.edit_contact_header_name)

    override fun getSaveButtonText(): String = getString(R.string.save_contact_button)

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val allTimezonesList: List<String> =
            TimeZone.getAvailableIDs()
                .toMutableList()
                .also { it.add(index = 0, element = "Use Computer Settings") }
                .toList()

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            allTimezonesList
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        headerBinding.apply {
            textViewDetailScreenSubscribe.setOnClickListener {
                lifecycleScope.launch(viewModel.mainImmediate) {
                    viewModel.toSubscriptionDetailScreen()
                }
            }
        }

        fragmentShareTimezone.apply {

            spinnerTimezones.let {
                it.adapter = spinnerAdapter
                it.setSelection(0)

                disableSpinner(
                    spinner = it,
                    spinnerLabel = textViewContactTimezone
                )

                it.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        parent?.setSelection(position)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            switchEditTimezone.setOnCheckedChangeListener { _, isChecked ->
                spinnerTimezones.let {
                    if (!isChecked) {
                        disableSpinner(spinner = it, spinnerLabel = textViewContactTimezone)
                    } else {
                        enableSpinner(spinner = it, spinnerLabel = textViewContactTimezone)
                    }
                }
            }
        }
    }

    private fun disableSpinner(spinner: Spinner, spinnerLabel: AppCompatTextView) {
        spinner.apply {
            isEnabled = false
            isClickable = false
            alpha = 0.5f
            setSelection(0)
        }

        spinnerLabel.setTextColorExt(color.secondaryText)
    }

    private fun enableSpinner(spinner: Spinner, spinnerLabel: AppCompatTextView) {
        spinner.apply {
            isEnabled = true
            isClickable = true
            alpha = 1.0f
        }

        spinnerLabel.setTextColorExt(color.text)
    }
}
