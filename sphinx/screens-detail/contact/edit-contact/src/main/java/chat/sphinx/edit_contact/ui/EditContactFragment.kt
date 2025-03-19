package chat.sphinx.edit_contact.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.contact.databinding.LayoutContactBinding
import chat.sphinx.contact.databinding.LayoutContactDetailScreenHeaderBinding
import chat.sphinx.contact.databinding.LayoutContactSaveBinding
import chat.sphinx.contact.ui.ContactFragment
import chat.sphinx.contact.ui.ContactViewState
import chat.sphinx.detail_resources.databinding.ShareTimezoneLayoutBinding
import chat.sphinx.edit_contact.R
import chat.sphinx.edit_contact.databinding.FragmentEditContactBinding
import chat.sphinx.resources.R.color
import chat.sphinx.resources.setTextColorExt
import chat.sphinx.wrapper_chat.isTrue
import chat.sphinx.wrapper_common.DateTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.annotation.meta.Exhaustive
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

    private var selectedTimezoneIdentifier: String? = null
    private var timezoneUpdated: Boolean = false
    private var timezoneEnabled: Boolean = false
    private lateinit var allTimezonesList: List<String>

    override fun getHeaderText(): String = getString(R.string.edit_contact_header_name)

    override fun getSaveButtonText(): String = getString(R.string.save_contact_button)

    companion object {
        const val USE_COMPUTER_SETTINGS = "Use Computer Settings"
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        allTimezonesList = DateTime.getValidTimeZoneIds()
            .toMutableList()
            .also { it.add(index = 0, element = USE_COMPUTER_SETTINGS) }
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

                handleSpinnerAndSwitch(
                    spinner = it,
                    spinnerLabel = textViewContactTimezone,
                    switch = switchEditTimezone,
                    isChecked = false
                )

                it.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (it.selectedItemPosition == 0) {
                            selectedTimezoneIdentifier = null
                        } else {
                            selectedTimezoneIdentifier = parent?.getItemAtPosition(position).toString()
                        }
                        timezoneUpdated = true
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            switchEditTimezone.setOnCheckedChangeListener { compoundButton, isChecked ->
                timezoneEnabled = isChecked

                handleSpinnerAndSwitch(
                    spinnerTimezones,
                    textViewContactTimezone,
                    switchEditTimezone,
                    isChecked,
                )
            }
        }

        contactSaveBinding.buttonSave.setOnClickListener {
            viewModel.updateTimezoneStatus(
                isTimezoneEnabled = timezoneEnabled,
                timezoneIdentifier = selectedTimezoneIdentifier,
                timezoneUpdated = timezoneUpdated
            )

            viewModel.closeFragment()
        }
    }

    private fun handleSpinnerAndSwitch(
        spinner: Spinner,
        spinnerLabel: AppCompatTextView,
        switch: SwitchCompat,
        isChecked: Boolean
    ) {
        val textColor = if (isChecked) color.text else color.secondaryText

        spinner.apply {
            isEnabled = isChecked
            isClickable = isChecked
            alpha = if (isChecked) 1.0f else 0.5f

            if (!isChecked) setSelection(0)
        }

        switch.isChecked = isChecked

        spinnerLabel.setTextColorExt(textColor)
    }

    override suspend fun onViewStateFlowCollect(viewState: ContactViewState) {
        @Exhaustive
        when (viewState) {
            ContactViewState.Error -> {}
            ContactViewState.Idle -> {}
            ContactViewState.Saved -> {}
            ContactViewState.Saving -> {}
            is ContactViewState.ShareTimezone -> {
                fragmentShareTimezone.also { timezoneLayout ->
                    val spinnerPos = allTimezonesList.indexOf(
                        viewState.chat.timezoneIdentifier?.value ?: USE_COMPUTER_SETTINGS
                    )

                    val spinnerSelection = if (spinnerPos == -1) 0 else spinnerPos

                    handleSpinnerAndSwitch(
                        spinner = timezoneLayout.spinnerTimezones,
                        spinnerLabel = timezoneLayout.textViewContactTimezone,
                        switch = timezoneLayout.switchEditTimezone,
                        isChecked = viewState.chat.timezoneEnabled?.isTrue() ?: false
                    )

                    timezoneLayout.spinnerTimezones.setSelection(spinnerSelection)
                }
            }
        }
    }
}
