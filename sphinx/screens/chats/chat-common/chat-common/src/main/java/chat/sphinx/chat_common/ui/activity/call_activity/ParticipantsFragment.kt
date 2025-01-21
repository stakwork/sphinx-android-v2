package chat.sphinx.chat_common.ui.activity.call_activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import chat.sphinx.chat_common.R
import chat.sphinx.chat_common.databinding.BottomSheetBinding
import chat.sphinx.concept_image_loader.ImageLoader
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
class ParticipantsBottomSheetFragment(
    private val participants: List<Participant>
) : BottomSheetDialogFragment() {

    @Inject
    lateinit var imageLoader: ImageLoader<ImageView>

    private lateinit var adapter: ParticipantAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = BottomSheetBinding.inflate(inflater, container, false)


        adapter = ParticipantAdapter(requireContext(), participants, imageLoader, lifecycleScope)
        binding.listView.adapter = adapter

        // Set participant count text
        val participantCountTextView: TextView = binding.root.findViewById(R.id.participantCountText)
        if (participants.size == 1) {
            participantCountTextView.text = getString(R.string.one_participant)
        } else {
            participantCountTextView.text = getString(R.string.participant_count, participants.size)
        }

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        return binding.root
    }


    class ParticipantAdapter(
        context: Context,
        private val participants: List<Participant>,
        private val imageLoader: ImageLoader<ImageView>,
        private val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope
    ) : ArrayAdapter<Participant>(context, 0, participants) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.participant_list, parent, false)

            val participant = getItem(position)
            val nameTextView: TextView = view.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = view.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = view.findViewById(R.id.micStatus)
            val profileImageView: ImageView = view.findViewById(R.id.profileImageView)
            val textViewInitials: TextView = view.findViewById(R.id.textViewInitials)

            nameTextView.text = participant?.name

            if (participant?.isCameraEnabled() == true) {
                cameraStatusImageView.visibility = View.VISIBLE
                cameraStatusImageView.setImageResource(R.drawable.camera)
            } else {
                cameraStatusImageView.visibility = View.GONE
            }

            micStatusImageView.setImageResource(
                if (participant?.isMicrophoneEnabled() == true) R.drawable.mic else R.drawable.mic_off
            )

            val metaDataJson = participant?.metadata
            val participantMetaData = metaDataJson?.toParticipantMetaDataOrNull(Moshi.Builder().build())

            participantMetaData?.profilePictureUrl?.let { imageUrl ->
                profileImageView.setImageDrawable(null)
                lifecycleScope.launch {
                    imageLoader.load(profileImageView, imageUrl)
                    profileImageView.visibility = View.VISIBLE
                    textViewInitials.visibility = View.GONE
                }
            } ?: run {
                val initials = participant?.name?.getInitials()
                profileImageView.apply {
                    visibility = View.GONE
                }

                textViewInitials.apply {
                    visibility = View.VISIBLE
                    if (!initials.isNullOrEmpty()) {

                        text = initials.toUpperCase(Locale.getDefault())
                    }
                    setBackgroundRandomColor(R.drawable.circle_icon_4)
                }
            }

            return view
        }
    }
}
