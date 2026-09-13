package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.AppDatabase
import com.example.data.PersonRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AppThemeProvider
import com.example.viewmodel.PersonViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "person_db"
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8
        ).build()
        val repository = PersonRepository(db, db.personDao(), db.householdDao(), db.personHistoryDao())
        val excelImportUseCase = com.example.domain.ExcelImportUseCase(db)
        val syncHelper = com.example.data.sync.RoomFirestoreSyncHelper(applicationContext, repository)

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PersonViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PersonViewModel(repository, excelImportUseCase, syncHelper) as T
                }
                if (modelClass.isAssignableFrom(com.example.ui.DiagnosticViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return com.example.ui.DiagnosticViewModel(
                        repository,
                        try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }

        setContent {
            AppThemeProvider {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: PersonViewModel = viewModel(factory = factory)
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        viewModel = viewModel,
                        repository = repository,
                        firestore = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

