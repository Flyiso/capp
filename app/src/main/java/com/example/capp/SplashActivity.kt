package com.example.capp

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
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imageView = findViewById<ImageView>(R.id.splashGif)

        Glide.with(this)
            .asGif()
            .load(R.raw.splash)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(object : CustomTarget<GifDrawable>() {

                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    imageView.setImageDrawable(resource)
                    resource.setLoopCount(1)

                    resource.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable?) {
                            startMainActivity()
                        }
                    })
                    resource.start()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Intentionally left blank: required by Glide for cleanup
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    startMainActivity()
                }
            })
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}