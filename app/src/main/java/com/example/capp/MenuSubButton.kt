package com.example.capp

import android.view.View

abstract class MenuSubButton(
    val view: View

) {
    val buttonId: Int

    init {
        // Grab the next available number and increment the global counter
        buttonId = nextId
        nextId++
    }
    abstract fun onClickAction()
    companion object {
        // Starts at 1 so your multiplier math doesn't multiply by 0
        private var nextId = 1
    }
}