package com.securechat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.securechat.R

/**
 * Bottom sheet shown when user taps the attachment (📎) button.
 * Options: Photo/Video, Camera, File.
 */
class AttachmentBottomSheet : BottomSheetDialogFragment() {

    var onPhotoSelected: (() -> Unit)? = null
    var onCameraSelected: (() -> Unit)? = null
    var onFileSelected: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_attachment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.optionPhoto).setOnClickListener {
            dismiss()
            onPhotoSelected?.invoke()
        }

        view.findViewById<View>(R.id.optionCamera).setOnClickListener {
            dismiss()
            onCameraSelected?.invoke()
        }

        view.findViewById<View>(R.id.optionFile).setOnClickListener {
            dismiss()
            onFileSelected?.invoke()
        }
    }

    companion object {
        const val TAG = "AttachmentBottomSheet"
    }
}
