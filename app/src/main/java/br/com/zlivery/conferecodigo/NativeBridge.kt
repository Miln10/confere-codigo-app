package br.com.zlivery.conferecodigo

import android.content.Intent
import android.webkit.JavascriptInterface

/**
 * Ponte JS -> nativo exposta como `window.AndroidNative` na WebView principal.
 * O site (confere-o-codigo) detecta a presença deste objeto via feature-detection
 * (ver proceedToPlatform em index.html) e, só quando existir, chama openConfirm
 * em vez de navegar para a URL de confirmação no próprio navegador.
 */
class NativeBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun openConfirm(platform: String, locatorCode: String) {
        activity.runOnUiThread {
            val intent = Intent(activity, ConfirmActivity::class.java)
            intent.putExtra(ConfirmActivity.EXTRA_PLATFORM, platform)
            intent.putExtra(ConfirmActivity.EXTRA_LOCATOR, locatorCode)
            activity.startActivity(intent)
        }
    }
}
