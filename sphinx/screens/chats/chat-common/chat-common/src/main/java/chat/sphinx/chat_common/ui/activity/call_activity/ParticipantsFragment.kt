package chat.sphinx.chat_common.ui.activity.call_activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import chat.sphinx.chat_common.R
import chat.sphinx.chat_common.databinding.BottomSheetBinding
import chat.sphinx.concept_image_loader.ImageLoader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.squareup.moshi.Moshi
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
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

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    // Custom Adapter for ListView
    class ParticipantAdapter(
        context: Context,
        private val participants: List<Participant>,
        private val imageLoader: ImageLoader<ImageView>,  // Inject ImageLoader
        private val lifecycleScope: androidx.lifecycle.LifecycleCoroutineScope // Pass lifecycleScope to manage coroutines
    ) : ArrayAdapter<Participant>(context, 0, participants) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.participant_list, parent, false)

            val participant = getItem(position)
            val nameTextView: TextView = view.findViewById(R.id.participantName)
            val cameraStatusImageView: ImageView = view.findViewById(R.id.cameraStatus)
            val micStatusImageView: ImageView = view.findViewById(R.id.micStatus)
            val profileImageView: ImageView = view.findViewById(R.id.profileImageView) // Ensure this is in the layout

            nameTextView.text = participant?.name

            // Set the camera and mic status
            cameraStatusImageView.setImageResource(
                if (participant?.isCameraEnabled() == true) R.drawable.outline_videocam_24 else R.drawable.outline_videocam_off_24
            )
            micStatusImageView.setImageResource(
                if (participant?.isMicrophoneEnabled() == true) R.drawable.outline_mic_24 else R.drawable.outline_mic_off_24
            )

            // Load profile picture using ImageLoader
            val metaDataJson = participant?.metadata
            val participantMetaData = metaDataJson?.toParticipantMetaDataOrNull(Moshi.Builder().build())

            participantMetaData?.profilePictureUrl?.let { imageUrl ->
                // Use lifecycleScope to launch the coroutine in the correct scope
                lifecycleScope.launch {
                    imageLoader.load(profileImageView, imageUrl)
                }
            } ?: run {
                // If no profile picture URL, set a default image
                profileImageView.setImageResource(chat.sphinx.resources.R.drawable.ic_baseline_person_32)
            }

            // Set OnClickListeners for camera and mic
            cameraStatusImageView.setOnClickListener {
                // Toggle camera status (this example assumes a toggle function exists)
                if (participant != null) {
                    toggleCameraStatus(participant)
                }
            }

            micStatusImageView.setOnClickListener {
                // Toggle mic status (this example assumes a toggle function exists)
                if (participant != null) {
                    toggleMicStatus(participant)
                }
            }

            return view
        }
        private fun toggleCameraStatus(participant: Participant) {
            // Logic for toggling camera (update participant's camera state)
            // Example: You would call your ViewModel or some method here to update the participant's camera state
            //participant.toggleCamera()  // You need to implement this or call a relevant method
        }

        private fun toggleMicStatus(participant: Participant) {
            // Logic for toggling microphone (update participant's mic state)
            // Example: You would call your ViewModel or some method here to update the participant's mic state
            //participant.toggleMic()  // You need to implement this or call a relevant method
        }
    }
}
