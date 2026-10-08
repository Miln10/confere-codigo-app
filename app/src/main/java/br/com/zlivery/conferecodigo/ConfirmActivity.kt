package br.com.zlivery.conferecodigo

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Abre a tela REAL de confirmação de entrega (iFood ou 99Food, domínio deles,
 * fora do nosso controle) numa WebView própria e digita o localizador sozinho,
 * assim que a página termina de carregar.
 *
 * Técnica confirmada ao vivo nas duas páginas reais (08/10/2026, não é suposição):
 * usar o setter nativo de HTMLInputElement.value + dispatchEvent(new Event('input',
 * {bubbles:true})) engana os frameworks (Vue no 99Food, React no iFood) fazendo-os
 * acreditar que foi o usuário quem digitou — só setar `.value` direto NÃO funciona
 * (o estado interno do componente não muda e o botão de continuar não habilita).
 *
 * O código de 4 dígitos que o CLIENTE informa (customerCode/delivery_code) nunca é
 * preenchido automaticamente — só o localizador, por decisão do usuário.
 */
class ConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_LOCATOR = "locator"
        private const val IFOOD_URL = "https://confirmacao-entrega-propria.ifood.com.br"
        private const val FOOD99_URL = "https://food-b-h5.99app.com/pt-BR/v2/confirmation-entrega"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: "ifood"
        val locator = intent.getStringExtra(EXTRA_LOCATOR) ?: ""
        // JSONObject.quote devolve a string já entre aspas e escapada — uso isso pra
        // embutir o valor dentro do JS injetado sem risco de quebrar a sintaxe.
        val locatorJs = JSONObject.quote(locator)

        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true

        val targetUrl = if (platform == "99food") FOOD99_URL else IFOOD_URL
        val fillScript = if (platform == "99food") food99FillScript(locatorJs) else ifoodFillScript(locatorJs)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                // Só injeta na própria página de destino, não em redirects/recursos internos.
                if (url != null && url.startsWith(targetUrl)) {
                    view.evaluateJavascript(fillScript, null)
                }
            }
        }
        webView.loadUrl(targetUrl)
    }

    /**
     * 99Food: 8 <input maxlength="1"> sequenciais, cada um `disabled` até o anterior
     * ser preenchido (confirmado ao vivo) — por isso preenche um de cada vez, com uma
     * pequena espera entre eles pra dar tempo do Vue reagir e habilitar o próximo.
     * Botão "Verificar e continuar" não tem um seletor estável (sem data-testid);
     * identificado pelo texto.
     */
    private fun food99FillScript(locatorJs: String) = """
        (async () => {
          try {
            const sleep = ms => new Promise(r => setTimeout(r, ms));
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            const digits = String($locatorJs).split('');
            for (let i = 0; i < digits.length; i++) {
              const inputs = document.querySelectorAll('input');
              const el = inputs[i];
              if (!el) break;
              setter.call(el, digits[i]);
              el.dispatchEvent(new Event('input', { bubbles: true }));
              await sleep(150);
            }
            await sleep(300);
            const btn = [...document.querySelectorAll('button')].find(b => /verificar|continuar/i.test(b.innerText));
            if (btn && !btn.className.includes('disabled')) btn.click();
          } catch (e) {}
        })();
    """.trimIndent()

    /**
     * iFood: a página abre numa tela intermediária ("Você chegou no local de
     * entrega?") antes do campo de localizador — precisa clicar em
     * [data-testid=arrived-at-consumer-button] primeiro. Os 8 dígitos ficam em
     * [data-testid=order-number-input-0..7] (todos habilitados de uma vez, ao
     * contrário do 99Food) e o botão é [data-testid=continue-button].
     */
    private fun ifoodFillScript(locatorJs: String) = """
        (async () => {
          try {
            const sleep = ms => new Promise(r => setTimeout(r, ms));
            const arrived = document.querySelector('[data-testid="arrived-at-consumer-button"]');
            if (arrived) { arrived.click(); await sleep(1200); }
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            const digits = String($locatorJs).split('');
            for (let i = 0; i < digits.length; i++) {
              const el = document.querySelector('[data-testid="order-number-input-' + i + '"]');
              if (!el) break;
              setter.call(el, digits[i]);
              el.dispatchEvent(new Event('input', { bubbles: true }));
              await sleep(100);
            }
            await sleep(300);
            const btn = document.querySelector('[data-testid="continue-button"]');
            if (btn && !btn.disabled) btn.click();
          } catch (e) {}
        })();
    """.trimIndent()
}
