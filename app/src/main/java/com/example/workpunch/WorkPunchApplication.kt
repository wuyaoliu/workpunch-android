package com.example.workpunch

import android.app.Application
import com.example.workpunch.data.AppDatabase
import com.example.workpunch.data.PunchRepository

class WorkPunchApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { PunchRepository(database.punchDao()) }
}
