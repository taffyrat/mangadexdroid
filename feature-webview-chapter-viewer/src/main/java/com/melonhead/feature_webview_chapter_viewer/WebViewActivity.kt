package com.melonhead.feature_webview_chapter_viewer

import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.melonhead.feature_webview_chapter_viewer.viewmodels.WebViewViewModel
import com.melonhead.data_shared.models.ui.UIChapter
import com.melonhead.data_shared.models.ui.UIManga
import com.melonhead.lib_core.scenes.CloseBanner
import org.koin.androidx.viewmodel.ext.android.viewModel

internal class WebViewActivity : ComponentActivity() {
    private val viewModel by viewModel<WebViewViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContent {
            com.melonhead.lib_core.theme.MangadexFollowerTheme {
                val url by viewModel.url.observeAsState()

                WebView(url = url, callClose = {
                    viewModel.markAsRead()
                    finish()
                })
            }
        }

        viewModel.parseIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.parseIntent(intent)
    }

    companion object {
        const val EXTRA_UICHAPTER = "EXTRA_UICHAPTER"
        const val EXTRA_UIMANGA = "EXTRA_UIMANGA"

        internal fun newIntent(context: Context, uiChapter: UIChapter, uiManga: UIManga): Intent {
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra(EXTRA_UICHAPTER, uiChapter)
            intent.putExtra(EXTRA_UIMANGA, uiManga)
            return intent
        }
    }
}


@Composable
private fun WebView(url: String?, callClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CloseBanner(title = "", onDoneTapped = callClose)

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                WebView(it).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.proceed()
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    if (url != null) {
                        loadUrl(url)
                    }
                }
            }, update = {
                if (url != null) {
                    it.loadUrl(url)
                }
            })
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun ScreenPreview() {
    Surface {
        WebView("") { }
    }
}
