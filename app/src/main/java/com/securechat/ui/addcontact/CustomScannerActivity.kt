package com.securechat.ui.addcontact

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.securechat.R

class CustomScannerActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_custom_scanner)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
