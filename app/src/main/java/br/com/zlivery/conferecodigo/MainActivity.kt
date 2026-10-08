package br.com.zlivery.conferecodigo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Casca fina em volta do site de produção do "Confere o Código"
 * (https://miln10.github.io/confere-o-codigo/) — câmera, OCR e toda a
 * lógica de reconhecimento de nota continuam 100% no site, sem nenhuma
 * duplicação aqui. A única coisa nova é a ponte `AndroidNative` (ver
 * NativeBridge.kt), que o site chama só quando o código extraído é um
 * localizador de 8 caracteres.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* resultado tratado pelo onPermissionRequest da WebView */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // getUserMedia já é iniciado por toque do usuário na tela anterior
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Concede só o que a própria página pediu (vídeo, pro getUserMedia da câmera).
                val granted = request.resources.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }.toTypedArray()
                if (granted.isNotEmpty()) request.grant(granted) else request.deny()
            }
        }

        webView.addJavascriptInterface(NativeBridge(this), "AndroidNative")

        // BUG corrigido (08/10/2026): a WebView é sempre recriada do zero aqui (nunca
        // guardamos/restauramos o estado dela), mas o loadUrl só rodava quando
        // savedInstanceState == null. Quando o Android mata o processo em segundo plano
        // (comum, principalmente com pouca RAM) e o usuário reabre tocando no ícone, o
        // sistema recria a Activity com savedInstanceState preenchido — a WebView nova
        // ficava vazia pra sempre (tela em branco), porque o loadUrl nunca rodava nesse
        // caso. Sempre carregar resolve: não há nada de útil pra restaurar de qualquer
        // forma, então recarregar do zero é o comportamento certo nos dois casos.
        webView.loadUrl("https://miln10.github.io/confere-o-codigo/")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
