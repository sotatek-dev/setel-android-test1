package com.example.locationdetect

import androidx.appcompat.app.AppCompatActivity


abstract class BaseActivity : AppCompatActivity() {
    fun getRepository() = (application as DetectLocationApp).getRepository()
}