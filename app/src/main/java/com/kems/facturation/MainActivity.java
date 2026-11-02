
package com.kems.facturation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String SETTINGS_PIN = "1707";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.rgb(15, 35, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 35, 30));

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        // Gestion des confirmations JavaScript
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    JsResult result
            ) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Confirmation")
                        .setMessage(message)
                        .setPositiveButton("Oui", (dialog, which) ->
                                result.confirm())
                        .setNegativeButton("Annuler", (dialog, which) ->
                                result.cancel())
                        .setOnCancelListener(dialog ->
                                result.cancel())
                        .show();

                return true;
            }

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    JsResult result
            ) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("KEMS Facturation")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) ->
                                result.confirm())
                        .setOnCancelListener(dialog ->
                                result.confirm())
                        .show();

                return true;
            }
        });

        // Connexion entre Java et JavaScript
        webView.addJavascriptInterface(
                new AndroidBridge(this),
                "KemsAndroid"
        );

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class AndroidBridge {

        private final Context context;

        AndroidBridge(Context c) {
            context = c;
        }

        // Vérification du code PIN des paramètres
        @JavascriptInterface
        public void openSettingsWithPin() {

            runOnUiThread(() -> {

                EditText pinInput = new EditText(context);

                pinInput.setHint("Saisir le code PIN");
                pinInput.setInputType(
                        InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
                );

                pinInput.setSingleLine(true);

                AlertDialog dialog = new AlertDialog.Builder(context)
                        .setTitle("Accès aux paramètres")
                        .setMessage("Veuillez saisir votre code PIN.")
                        .setView(pinInput)
                        .setPositiveButton("Valider", null)
                        .setNegativeButton("Annuler", (d, which) -> {
                            sendPinResult(false);
                        })
                        .create();

                dialog.setOnShowListener(d -> {

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(v -> {

                                String enteredPin =
                                        pinInput.getText().toString();

                                if (SETTINGS_PIN.equals(enteredPin)) {

                                    sendPinResult(true);
                                    dialog.dismiss();

                                } else {

                                    pinInput.setError("Code PIN incorrect");
                                    pinInput.setText("");
                                }
                            });
                });

                dialog.setOnCancelListener(d ->
                        sendPinResult(false));

                dialog.show();
            });
        }

        // Retour du résultat vers index.html
        private void sendPinResult(boolean valid) {

            if (webView == null) return;

            runOnUiThread(() -> {

                webView.evaluateJavascript(
                        "if(window.kemsSettingsPinResult){" +
                        "window.kemsSettingsPinResult(" + valid + ");" +
                        "}",
                        null
                );
            });
        }

        // Impression de facture en PDF
        @JavascriptInterface
        public void printInvoice(String html) {

            runOnUiThread(() -> {

                WebView printView = new WebView(context);

                printView.getSettings().setJavaScriptEnabled(false);

                printView.setWebViewClient(new WebViewClient() {

                    @Override
                    public void onPageFinished(WebView view, String url) {

                        PrintManager manager =
                                (PrintManager) getSystemService(
                                        Context.PRINT_SERVICE
                                );

                        String jobName = "KEMS_Facture";

                        manager.print(
                                jobName,
                                view.createPrintDocumentAdapter(jobName),
                                new PrintAttributes.Builder()
                                        .setMediaSize(
                                                PrintAttributes.MediaSize.ISO_A4
                                        )
                                        .setResolution(
                                                new PrintAttributes.Resolution(
                                                        "pdf",
                                                        "PDF",
                                                        300,
                                                        300
                                                )
                                        )
                                        .setMinMargins(
                                                PrintAttributes.Margins.NO_MARGINS
                                        )
                                        .build()
                        );
                    }
                });

                printView.loadDataWithBaseURL(
                        null,
                        html,
                        "text/HTML",
                        "UTF-8",
                        null
                );
            });
        }
    }
}
