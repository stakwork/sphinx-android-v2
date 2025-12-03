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
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
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

        adapter = ParticipantAdapter(
            requireContext(),
            participants,
            imageLoader,
            lifecycleScope,
            participantColors
        )
        binding.participantsRv.adapter = adapter

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
        this.participants = newParticipants
        this.participantColors = newParticipantColors

        if (_binding != null) { // Ensure the view exists
            updateParticipantCount()
            adapter.setParticipants(participants, participantColors)
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
    ) : RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

        private val moshi: Moshi =
            Moshi.Builder().build() // Reuse instead of recreating in getView()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.participant_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val participant = participants[position]

            // Set participant name
            viewHolder.nameTextView.text = participant.name

            // Camera status
            viewHolder.cameraStatusImageView.visibility =
                if (participant.isCameraEnabled) View.VISIBLE else View.GONE
            if (viewHolder.cameraStatusImageView.isVisible) {
                viewHolder.cameraStatusImageView.setImageResource(R.drawable.camera)
            }

            // Microphone status
            viewHolder.micStatusImageView.setImageResource(
                if (participant.isMicrophoneEnabled) R.drawable.mic else R.drawable.mic_off
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
        }

        override fun getItemCount(): Int = participants.size

        fun setParticipants(
            participants: MutableList<Participant>,
            participantColors: MutableMap<String, Int>
        ) {
            this.participants = participants
            this.participantColors = participantColors
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = itemView.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = itemView.findViewById(R.id.micStatus)
            val profileImageView: ImageView = itemView.findViewById(R.id.profileImageView)
            val textViewInitials: TextView = itemView.findViewById(R.id.textViewInitials)
        }

    }
}