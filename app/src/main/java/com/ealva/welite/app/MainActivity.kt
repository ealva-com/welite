package com.ealva.welite.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ealva.welite.sql.Database

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    val db = Database()
    super.onCreate(savedInstanceState)

  }
}
