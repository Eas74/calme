package fr.perso.calme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Calme"
        /** Plafond quotidien de publications. Modifier ici. */
        private const val DAILY_LIMIT = 30
        /** Masque l'appli dans les captures d'écran et les aperçus récents. */
        private const val SECURE_WINDOW = true
    }

    private lateinit var root: LinearLayout
    private lateinit var web: WebView
    private lateinit var counter: TextView
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var quota: Quota

    private var scrollLocked = true
    private var locked = true
    private var prompting = false
    private var lastRedirectMs = 0L
    private var fallbackScript: String? = null

    // ---------------------------------------------------------------- cycle de vie

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SECURE_WINDOW) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        quota = Quota(PrefsStore(getSharedPreferences("quota", Context.MODE_PRIVATE)), { LocalDate.now().toString() }, DAILY_LIMIT)
        buildUi()
        configureWebView()
        installScrollLock()
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }
        updateCounter()
    }

    override fun onStart() {
        super.onStart()
        if (locked && !prompting) authenticate()
    }

    override fun onStop() {
        super.onStop()
        locked = true
        root.visibility = View.INVISIBLE   // reverrouillage à chaque sortie de l'appli
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- verrouillage

    private fun authenticate() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.no_lock, Toast.LENGTH_LONG).show()
            finish()   // fail closed : la session Instagram ne doit pas être accessible sans verrou
            return
        }
        prompting = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setAllowedAuthenticators(auth)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked = false
                root.visibility = View.VISIBLE
                if (web.url == null) web.loadUrl(UrlPolicy.HOME_FOLLOWING)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                finish()
            }
        }).authenticate(info)
    }

    // ---------------------------------------------------------------- interface

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(textRes: Int, weight: Float, onClick: () -> Unit) = Button(this).apply {
        setText(textRes)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
    }

    private fun buildUi() {
        web = WebView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        counter = TextView(this).apply { gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) }
        prevBtn = button(R.string.prev, 0.6f) { web.evaluateJavascript("window.__calm&&window.__calm.prev()", null) }
        nextBtn = button(R.string.next, 1.2f) { onNext() }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(button(R.string.feed, 1f) { web.loadUrl(UrlPolicy.HOME_FOLLOWING) })
            addView(button(R.string.messages, 1f) { web.loadUrl(UrlPolicy.INBOX) })
            addView(prevBtn); addView(nextBtn); addView(counter)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            visibility = View.INVISIBLE
            addView(web); addView(bar)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
    }

    private fun updateCounter() {
        counter.text = getString(R.string.counter, quota.used, quota.dailyLimit)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun onNext() {
        if (quota.remaining == 0) { toast(getString(R.string.limit_reached, quota.dailyLimit)); return }
        web.evaluateJavascript("window.__calm ? window.__calm.next() : false") { result ->
            if (result == "true") { quota.tryConsume(); updateCounter() }
            else toast(getString(R.string.nothing_more))
        }
    }

    // ---------------------------------------------------------------- WebView

    private fun readAsset(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    @SuppressLint("SetJavaScriptEnabled")   // requis par instagram.com ; surface limitée par UrlPolicy
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        // Script injecté seulement sur www.instagram.com ; aucun addJavascriptInterface.
        val script = "window.__CALM_SELECTORS=" + readAsset("selectors.json") + ";\n" + readAsset("inject.js")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf("https://www.instagram.com"))
        } else {
            fallbackScript = script   // injecté dans onPageFinished
        }

        web.webViewClient = object : WebViewClient() {
            // Navigations « classiques » (liens, loadUrl)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val ok = UrlPolicy.isPageAllowed(request.url.toString())
                if (!ok) toast(getString(R.string.blocked))
                return !ok
            }

            // Toutes les sous-ressources : tout domaine non autorisé est coupé
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (UrlPolicy.isRequestAllowed(request.url.toString())) return null
                Log.d(TAG, "requête bloquée vers ${request.url.host}")   // hôte seulement, jamais l'URL complète
                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            // Instagram est une SPA (pushState) : shouldOverrideUrlLoading n'est PAS appelé
            // pour ces navigations, d'où ce second point de contrôle.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                onLocationChanged(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                fallbackScript?.let { if (url.startsWith("https://www.instagram.com")) view.evaluateJavascript(it, null) }
            }
        }
    }

    private fun onLocationChanged(url: String) {
        if (!url.startsWith("https://")) return
        if (!UrlPolicy.isPageAllowed(url)) { web.loadUrl(UrlPolicy.HOME_FOLLOWING); return }
        val now = System.currentTimeMillis()
        if (UrlPolicy.isUnfilteredHome(url) && now - lastRedirectMs > 2000) {   // garde anti-boucle
            lastRedirectMs = now
            web.loadUrl(UrlPolicy.HOME_FOLLOWING)
            return
        }
        scrollLocked = UrlPolicy.isScrollLocked(url)
        val vis = if (scrollLocked) View.VISIBLE else View.GONE
        prevBtn.visibility = vis; nextBtn.visibility = vis; counter.visibility = vis
    }

    /** Annule les glissements verticaux hors messagerie : indépendant du HTML d'Instagram. */
    @SuppressLint("ClickableViewAccessibility")
    private fun installScrollLock() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var blocking = false
        web.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; blocking = false }
                MotionEvent.ACTION_MOVE -> if (!blocking && scrollLocked && e.pointerCount == 1) {
                    val dx = abs(e.x - sx); val dy = abs(e.y - sy)
                    if (dy > slop && dy > dx) {
                        blocking = true
                        // La WebView a reçu le DOWN : on lui envoie un CANCEL propre
                        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                        v.onTouchEvent(cancel); cancel.recycle()
                    }
                }
            }
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                val was = blocking; blocking = false; was
            } else blocking
        }
    }

    private class PrefsStore(private val prefs: android.content.SharedPreferences) : Quota.Store {
        override fun read(): String? = prefs.getString("q", null)
        override fun write(value: String) { prefs.edit().putString("q", value).apply() }
    }
}
