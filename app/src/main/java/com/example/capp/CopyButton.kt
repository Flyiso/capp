package com.example.capp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast

class CopyButton(
    view: View,
    private val context: Context,
    private val rootView: View,
    private val getCurrentPalettes: () -> List<ColorPalette>,
    private val getCurrentColorMode: () -> String,
    ) : MenuSubButton(view) {

    override fun onClickAction() {
        addColorsToClipboard()
    }
    private fun addColorsToClipboard() {
        val activeColorMode = getCurrentColorMode()
        val colorClipboardContent = StringBuilder()
        val activePalettes = getCurrentPalettes()
        colorClipboardContent.append("Color profile: $activeColorMode")
        activePalettes.forEach { palette ->
            colorClipboardContent.append("\n${palette.returnCurrentColors(activeColorMode)}")
        }
        colorClipboardContent.toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText("copied colors", colorClipboardContent)
        clipboard.setPrimaryClip(clip)
        Toast.makeText( context, "$activeColorMode colors copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}


