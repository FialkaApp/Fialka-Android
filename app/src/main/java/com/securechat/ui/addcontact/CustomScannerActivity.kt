/*
 * SecureChat — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.securechat.ui.addcontact

import android.os.Bundle
import android.widget.ImageButton
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.securechat.R

class CustomScannerActivity : CaptureActivity() {

    private var torchOn = false

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_custom_scanner)
        return findViewById(R.id.zxing_barcode_scanner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btnTorch = findViewById<ImageButton>(R.id.btnTorch)
        val scanner = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)

        btnTorch.setOnClickListener {
            torchOn = !torchOn
            if (torchOn) {
                scanner.setTorchOn()
                btnTorch.setImageResource(R.drawable.ic_flashlight_on)
            } else {
                scanner.setTorchOff()
                btnTorch.setImageResource(R.drawable.ic_flashlight_off)
            }
        }
    }
}
