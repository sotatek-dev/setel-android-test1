package com.example.locationdetect

import android.app.Application

class DetectLocationApp : Application() {

  private lateinit var repository: ReminderRepository

  override fun onCreate() {
    super.onCreate()
    repository = ReminderRepository(this)
  }

  fun getRepository() = repository
}