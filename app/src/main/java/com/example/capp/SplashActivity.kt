package com.example.capp // ⚠️ CHANGE THIS to match your actual app package name

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imageView = findViewById<ImageView>(R.id.splashGif)

        Glide.with(this)
            .asGif()
            .load(R.raw.splash)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Eliminates lag by pre-caching frames
            .into(object : CustomTarget<GifDrawable>() {

                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    // 1. Attach the GIF to your ImageView
                    imageView.setImageDrawable(resource)

                    // 2. Tell the GIF to play exactly ONCE
                    resource.setLoopCount(1)

                    // 3. Set up the listener to detect when the GIF finishes playing
                    resource.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable?) {
                            startMainActivity()
                        }
                    })

                    // 4. Start the animation explicitly
                    resource.start()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Intentionally left blank: required by Glide for cleanup
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    // Fail-safe: If the GIF breaks or fails to load, immediately jump to MainActivity
                    startMainActivity()
                }
            })
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish() // Destroys SplashActivity so hitting "Back" doesn't return here
    }
}