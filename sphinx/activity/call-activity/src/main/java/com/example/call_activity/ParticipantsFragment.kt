package com.example.call_activity

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ParticipantsBottomSheetFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var imageLoader: ImageLoader<ImageView>

    @Inject
    lateinit var moshi: Moshi

    private var _binding: FragmentParticipantsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ParticipantAdapter

    private var participants: MutableList<Participant> = mutableListOf()
    private var participantColors: MutableMap<String, Int> = mutableMapOf()

    companion object {
        private const val PARTICIPANTS_KEY = "participants"
        private const val COLORS_KEY = "colors"

        fun newInstance(
            participants: List<Participant>,
            participantColors: Map<String, Int>
        ): ParticipantsBottomSheetFragment {
            return ParticipantsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(PARTICIPANTS_KEY, ArrayList(participants.map { ParticipantParcelable(it) }))
                    putSerializable(COLORS_KEY, HashMap(participantColors))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            participants = it.getParcelableArrayList<ParticipantParcelable>(PARTICIPANTS_KEY)
                ?.map { it.toParticipant() }
                ?.toMutableList() ?: mutableListOf()
            participantColors = (it.getSerializable(COLORS_KEY) as? Map<String, Int>)?.toMutableMap() ?: mutableMapOf()
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
            moshi,
            lifecycleScope,
            participantColors
        )
        binding.listView.adapter = adapter

        updateParticipantCount()

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        // Observe participant changes if needed
        (activity as? CallActivity)?.let { callActivity ->
            lifecycleScope.launchWhenStarted {
                callActivity.viewModel.participants.collectLatest { updatedParticipants ->
                    participants = updatedParticipants.toMutableList()
                    participantColors = callActivity.viewModel.participantColors
                    adapter.setParticipants(participants, participantColors)
                    updateParticipantCount()
                }
            }
        }
    }

    fun updateParticipants(newParticipants: List<Participant>, newColors: Map<String, Int>) {
        participants = newParticipants.toMutableList()
        participantColors = newColors.toMutableMap()
        if (::adapter.isInitialized) {
            adapter.setParticipants(participants, participantColors)
            updateParticipantCount()
        }
    }

    private fun updateParticipantCount() {
        binding.participantCountText.text = when (participants.size) {
            1 -> getString(R.string.one_participant)
            else -> getString(R.string.participant_count, participants.size)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ParticipantAdapter(
        context: Context,
        private var participants: MutableList<Participant>,
        private val imageLoader: ImageLoader<ImageView>,
        private val moshi: Moshi,
        private val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope,
        private var participantColors: MutableMap<String, Int>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = itemView.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = itemView.findViewById(R.id.micStatus)
            val profileImageView: ImageView = itemView.findViewById(R.id.profileImageView)
            val textViewInitials: TextView = itemView.findViewById(R.id.textViewInitials)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.participant_list_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val participant = participants[position]

            holder.nameTextView.text = participant.name

            // Camera status
            holder.cameraStatusImageView.visibility = if (participant.isCameraEnabled()) View.VISIBLE else View.GONE
            if (holder.cameraStatusImageView.visibility == View.VISIBLE) {
                holder.cameraStatusImageView.setImageResource(R.drawable.camera)
            }

            // Microphone status
            holder.micStatusImageView.setImageResource(
                if (participant.isMicrophoneEnabled()) R.drawable.mic else R.drawable.mic_off
            )


            val participantMetaData = participant.metadata?.toParticipantMetaDataOrNull(moshi)

            participantMetaData?.profilePictureUrl?.let { imageUrl ->
                holder.textViewInitials.visibility = View.GONE
                holder.profileImageView.visibility = View.VISIBLE
                holder.profileImageView.setImageDrawable(null)

                lifecycleScope.launch {
                    imageLoader.load(holder.profileImageView, imageUrl)
                }
            } ?: run {
                val initials = participant.name?.getInitials()

                holder.profileImageView.visibility = View.GONE
                holder.textViewInitials.apply {
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
            newParticipants: MutableList<Participant>,
            newParticipantColors: MutableMap<String, Int>
        ) {
            participants = newParticipants
            participantColors = newParticipantColors
            notifyDataSetChanged()
        }
    }
}

data class ParticipantParcelable(val participant: Participant) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {

    }

    override fun describeContents(): Int = 0

    fun toParticipant(): Participant = participant

    companion object CREATOR : android.os.Parcelable.Creator<ParticipantParcelable> {
        override fun createFromParcel(parcel: android.os.Parcel): ParticipantParcelable {
            return ParticipantParcelable(parcel)
        }

        override fun newArray(size: Int): Array<ParticipantParcelable?> {
            return arrayOfNulls(size)
        }
    }
}