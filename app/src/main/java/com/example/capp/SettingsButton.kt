package com.example.capp

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioGroup
import androidx.core.content.edit

class SettingsButton(
    view: View,
    private val context: Context,
    private val rootView: View,
    private val getCurrentColorMode: () -> String,
    private val getCurrentCameraMode: () -> String,
    private val onColorModeChanged: (String) -> Unit,
    private val onCameraModeChanged: (String) -> Unit
) : MenuSubButton(view) {

    override fun onClickAction() {
        showSettingsPopup()
    }

    private fun showSettingsPopup() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_settings, null)

        val width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
        val popupWindow = PopupWindow(popupView, width, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        val radioGroup = popupView.findViewById<RadioGroup>(R.id.radioGroupFormat)
        val activeColorMode = getCurrentColorMode()
        when (activeColorMode) {
            "RGB" -> radioGroup.check(R.id.radioRGB)
            "HEX" -> radioGroup.check(R.id.radioHEX)
            "HSV" -> radioGroup.check(R.id.radioHSV)
            "CMY" -> radioGroup.check(R.id.radioCMY)
            "NCS" -> radioGroup.check(R.id.radioNCS)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newColorMode = when (checkedId) {
                R.id.radioRGB -> "RGB"
                R.id.radioHEX -> "HEX"
                R.id.radioHSV -> "HSV"
                R.id.radioCMY -> "CMY"
                R.id.radioNCS -> "NCS"
                else -> "RGB"
            }

            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            prefs.edit { putString("color_mode", newColorMode) }

            onColorModeChanged(newColorMode)
        }

        val radioGroupCamera = popupView.findViewById<RadioGroup>(R.id.radioGroupCamera)
        val activeCameraMode = getCurrentCameraMode()

        when (activeCameraMode) {
            "BACK" -> radioGroupCamera.check(R.id.radioBACK)
            "FRONT" -> radioGroupCamera.check(R.id.radioFRONT)
        }
        radioGroupCamera.setOnCheckedChangeListener { _, checkedId ->
            val newCameraMode = when (checkedId) {
                R.id.radioBACK -> "BACK"
                R.id.radioFRONT -> "FRONT"
                else -> "BACK"
            }
            context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit {
                putString("camera_mode", newCameraMode)
            }
            onCameraModeChanged(newCameraMode)
        }
        rootView.alpha = 0.5f
        popupWindow.setOnDismissListener { rootView.alpha = 1.0f }
        popupWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0)
    }
}