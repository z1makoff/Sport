package com.z1makoff.sport

import android.app.Application
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.data.SportDatabase

class SportApp : Application() {
    val dao: SportDao by lazy { SportDatabase.create(this).dao() }
}
