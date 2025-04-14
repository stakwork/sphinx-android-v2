package com.example.call_activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
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
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ParticipantsBottomSheetFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var imageLoader: ImageLoader<ImageView>

    private var _binding: FragmentParticipantsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ParticipantAdapter
    private var participants: List<Participant> = emptyList()
    private var participantColors: Map<String, Int> = emptyMap()

    companion object {
        private const val ARG_PARTICIPANTS = "participants"
        private const val ARG_COLORS = "participant_colors"

        fun newInstance(
            participants: List<Participant>,
            participantColors: Map<String, Int>
        ): ParticipantsBottomSheetFragment {
            return ParticipantsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PARTICIPANTS, ArrayList(participants))
                    putSerializable(ARG_COLORS, HashMap(participantColors))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            participants = it.getParcelableArrayList<Participant>(ARG_PARTICIPANTS) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            participantColors = it.getSerializable(ARG_COLORS) as? Map<String, Int> ?: emptyMap()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParticipantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ParticipantAdapter(
            requireContext(),
            participants,
            imageLoader,
            viewLifecycleOwner.lifecycleScope,
            participantColors
        )
        binding.listView.adapter = adapter

        updateParticipantCount()

        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateParticipants(
        newParticipants: List<Participant>,
        newParticipantColors: Map<String, Int>
    ) {
        participants = newParticipants
        participantColors = newParticipantColors

        if (::adapter.isInitialized) {
            adapter.updateParticipants(newParticipants, newParticipantColors)
            updateParticipantCount()
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
        private var participants: List<Participant>,
        private val imageLoader: ImageLoader<ImageView>,
        private val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope,
        private var participantColors: Map<String, Int>
    ) : ArrayAdapter<Participant>(context, 0, participants) {

        private val moshi: Moshi = Moshi.Builder().build()

        override fun getCount(): Int = participants.size

        override fun getItem(position: Int): Participant? = participants.getOrNull(position)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val viewHolder: ViewHolder
            val view: View

            if (convertView == null) {
                view = LayoutInflater.from(context).inflate(R.layout.participant_list_item, parent, false)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else {
                view = convertView
                viewHolder = convertView.tag as ViewHolder
            }

            getItem(position)?.let { participant ->
                bindParticipant(viewHolder, participant)
            }

            return view
        }

        private fun bindParticipant(viewHolder: ViewHolder, participant: Participant) {
            // Set participant name
            viewHolder.nameTextView.text = participant.name

            // Camera status
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
        }

        fun updateParticipants(
            newParticipants: List<Participant>,
            newParticipantColors: Map<String, Int>
        ) {
            participants = newParticipants
            participantColors = newParticipantColors
            notifyDataSetChanged()
        }

        private class ViewHolder(view: View) {
            val nameTextView: TextView = view.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = view.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = view.findViewById(R.id.micStatus)
            val profileImageView: ImageView = view.findViewById(R.id.profileImageView)
            val textViewInitials: TextView = view.findViewById(R.id.textViewInitials)
        }
    }
}