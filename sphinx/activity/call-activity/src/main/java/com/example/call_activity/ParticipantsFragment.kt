package com.example.call_activity

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import chat.sphinx.call_activity.R
import chat.sphinx.call_activity.databinding.FragmentParticipantsBinding
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.resources.getRandomColor
import chat.sphinx.resources.setBackgroundRandomColor
import chat.sphinx.wrapper_common.util.getInitials
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.moshi.Moshi
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ParticipantsBottomSheetFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var imageLoader: ImageLoader<ImageView>

    private lateinit var adapter: ParticipantAdapter

    private var _binding: FragmentParticipantsBinding? = null
    private val binding get() = _binding!! // Only use when safe

    private var participants: MutableList<Participant> = mutableListOf()
    private var participantColors: MutableMap<String, Int> = mutableMapOf()

    companion object {
        fun newInstance(
            participants: MutableList<Participant>,
            participantColors: MutableMap<String, Int>
        ) = ParticipantsBottomSheetFragment().apply {
            this.participants = participants
            this.participantColors = participantColors
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParticipantsBinding.inflate(inflater, container, false)

        adapter = ParticipantAdapter(requireContext(), participants, imageLoader, lifecycleScope, participantColors)
        binding.listView.adapter = adapter

        updateParticipantCount()

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    fun setParticipants(
    newParticipants: MutableList<Participant>,
    newParticipantColors: MutableMap<String, Int>
) {
    this.participants.clear()
    this.participants.addAll(newParticipants)
    this.participantColors.clear()
    this.participantColors.putAll(newParticipantColors)

    if (isAdded && _binding != null) { // Check both isAdded and binding
        updateParticipantCount()
        adapter.setParticipants(participants, participantColors)
        binding.listView.invalidateViews()
    }
}

    private fun updateParticipantCount() {
        binding.participantCountText.text = when (participants.size) {
            1 -> getString(R.string.one_participant)
            else -> getString(R.string.participant_count, participants.size)
        }
    }

    class ParticipantAdapter(
        context: Context,
        private var participants: MutableList<Participant>,
        private val imageLoader: ImageLoader<ImageView>,
        private val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope,
        private var participantColors: MutableMap<String, Int>
    ) : ArrayAdapter<Participant>(context, 0, participants) {

        private val moshi: Moshi = Moshi.Builder().build() // Reuse instead of recreating in getView()
           override fun getItemId(position: Int): Long {
    return participants.getOrNull(position)?.sid?.hashCode()?.toLong() ?: super.getItemId(position)
}
       

            if (convertView == null) {
                view = LayoutInflater.from(context).inflate(R.layout.participant_list_item, parent, false)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else {
                view = convertView
                viewHolder = convertView.tag as ViewHolder
            }

            val participant = getItem(position) ?: return view

            // Set participant name
            viewHolder.nameTextView.text = participant.name

            // Camera status visibility
            viewHolder.cameraStatusImageView.apply {
                visibility = if (participant.isCameraEnabled()) View.VISIBLE else View.GONE
                if (visibility == View.VISIBLE) setImageResource(R.drawable.camera)
            }

            // Microphone status
            viewHolder.micStatusImageView.setImageResource(
                if (participant.isMicrophoneEnabled()) R.drawable.mic else R.drawable.mic_off
            )

            // Load profile picture or initials
            val participantMetaData = participant.metadata?.toParticipantMetaDataOrNull(moshi)

            participantMetaData?.profilePictureUrl?.let { imageUrl ->
                viewHolder.textViewInitials.visibility = View.GONE
                viewHolder.profileImageView.visibility = View.VISIBLE
                viewHolder.profileImageView.setImageDrawable(null)

                lifecycleScope.launch {
                    imageLoader.load(viewHolder.profileImageView, imageUrl)
                }
            } ?: run {
                val initials = participant.name?.getInitials()

                viewHolder.profileImageView.visibility = View.GONE
                viewHolder.textViewInitials.apply {
                    visibility = View.VISIBLE
                    text = initials?.uppercase(Locale.getDefault()) ?: ""

                    val sciKey = participant.getNonEmptySCI()
                    val color = participantColors[sciKey]
                    setBackgroundRandomColor(R.drawable.circle_icon_4, color)
                }
            }

            return view
        }
fun setParticipants(
    participants: MutableList<Participant>,
    participantColors: MutableMap<String, Int>
) {
    clear()
    addAll(participants)
    this.participantColors.clear()
    this.participantColors.putAll(participantColors)
    notifyDataSetChanged()
    notifyAll()
}
        // ViewHolder to optimize findViewById calls
        private class ViewHolder(view: View) {
            val nameTextView: TextView = view.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = view.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = view.findViewById(R.id.micStatus)
            val profileImageView: ImageView = view.findViewById(R.id.profileImageView)
            val textViewInitials: TextView = view.findViewById(R.id.textViewInitials)
        }
    }
}