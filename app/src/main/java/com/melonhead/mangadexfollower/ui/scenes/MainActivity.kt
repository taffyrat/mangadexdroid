package com.melonhead.mangadexfollower.ui.scenes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.melonhead.mangadexfollower.extensions.dateOrTimeString
import com.melonhead.mangadexfollower.models.ui.LoginStatus
import com.melonhead.mangadexfollower.models.ui.UIChapter
import com.melonhead.mangadexfollower.models.ui.UIManga
import com.melonhead.mangadexfollower.ui.theme.MangadexFollowerTheme
import com.melonhead.mangadexfollower.ui.viewmodels.MainViewModel
import kotlinx.datetime.Clock
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModel<MainViewModel>()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // do nothing for now
        }

        // request permission to post notifications
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MangadexFollowerTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val loginStatus by viewModel.loginStatus.observeAsState()
                    val manga by viewModel.manga.observeAsState(listOf())

                    Content(loginStatus = loginStatus,
                        manga = manga,
                        loginClicked = { username, password -> viewModel.authenticate(username, password) },
                        onChapterClicked = { viewModel.onChapterClicked(this, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun Content(loginStatus: LoginStatus?, manga: List<UIManga>, loginClicked: (username: String, password: String) -> Unit, onChapterClicked: (UIChapter) -> Unit) {
    when (loginStatus) {
        LoginStatus.LoggedIn -> if (manga.isEmpty()) LoadingScreen() else ChaptersList(manga, onChapterClicked = onChapterClicked)
        LoginStatus.LoggedOut -> LoginScreen(loginClicked)
        LoginStatus.LoggingIn, null -> LoadingScreen()
    }
}

@Composable
fun LoadingScreen() {
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(loginClicked: (email: String, password: String) -> Unit) {
    var emailField by rememberSaveable { mutableStateOf("") }
    var passwordField by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(value = emailField,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            onValueChange = { emailField = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        TextField(value = passwordField,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            onValueChange = { passwordField = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = {
            if (emailField.isNotBlank() && passwordField.isNotBlank())
                loginClicked(emailField, passwordField)
        }) {
            Text(text = "Sign In")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chapter(modifier: Modifier = Modifier, uiChapter: UIChapter, onChapterClicked: (UIChapter) -> Unit) {
    Card(modifier = modifier.fillMaxWidth(),
        onClick = {
        onChapterClicked(uiChapter)
    }) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f),
               verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "Chapter ${uiChapter.chapter}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp)
                Text(text = uiChapter.title ?: "",
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                )
                Text(text = uiChapter.createdDate.dateOrTimeString(),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp
                )
            }
            Text(modifier = Modifier.align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.primary,
                text = if (uiChapter.read != true) "NEW" else "",
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp)
        }
    }

}

@Composable
fun Manga(uiManga: UIManga, onChapterClicked: (UIChapter) -> Unit) {
    Column {
        Text(modifier = Modifier.padding(bottom = 8.dp),
            text = uiManga.title,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp)
        uiManga.chapters.forEach {
            Chapter(modifier = Modifier.padding(bottom = 8.dp),
                uiChapter = it,
                onChapterClicked = onChapterClicked)
        }
    }
}

@Composable
fun ChaptersList(manga: List<UIManga>, onChapterClicked: (UIChapter) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(manga) {
            Column(verticalArrangement = Arrangement.SpaceEvenly) {
                Manga(uiManga = it, onChapterClicked = onChapterClicked)
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun ChapterPreview() {
    MangadexFollowerTheme {
        Column {
            Chapter(uiChapter = UIChapter("", "101", "Test Title", Clock.System.now(), false), onChapterClicked = { })
            Chapter(uiChapter = UIChapter("", "102", "Test Title 2", Clock.System.now(), true), onChapterClicked = { })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MangaPreview() {
    MangadexFollowerTheme {
        Column(modifier = Modifier.fillMaxWidth()) {
            Manga(uiManga = UIManga("", "Test Manga", listOf()), onChapterClicked = { })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    MangadexFollowerTheme {
        LoginScreen(loginClicked = { _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingPreview() {
    MangadexFollowerTheme {
        LoadingScreen()
    }
}