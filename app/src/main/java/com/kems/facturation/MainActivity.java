
package com.kems.facturation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int SAVE_FILE_REQUEST = 1002;

    // Code PIN pour accéder aux paramètres
    private static final String SETTINGS_PIN = "1707";

    private String pendingJson;

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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            new String[]{
                                    "application/json",
                                    "text/json",
                                    "text/plain",
                                    "application/octet-stream"
                            }
                    );

                    startActivityForResult(
                            intent, FILE_CHOOSER_REQUEST);
                    return true;

                } catch (Exception e) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(this), "KemsAndroid");

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;

                if (resultCode == RESULT_OK && data != null
                        && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }

                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }

        if (requestCode == SAVE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null
                    && data.getData() != null && pendingJson != null) {
                try (OutputStream output =
                        getContentResolver().openOutputStream(
                                data.getData())) {

                    if (output == null) {
                        throw new IOException(
                                "Impossible d'ouvrir le fichier.");
                    }

                    output.write(
                            pendingJson.getBytes(StandardCharsets.UTF_8));
                    output.flush();

                    Toast.makeText(this,
                            "Sauvegarde exportée avec succès.",
                            Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    Toast.makeText(this,
                            "Erreur pendant l'exportation.",
                            Toast.LENGTH_LONG).show();
                }
            }

            pendingJson = null;
        }
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

        @JavascriptInterface
        public void openSettingsWithPin() {
            runOnUiThread(() -> {
                EditText pinInput = new EditText(MainActivity.this);
                pinInput.setInputType(
                        InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                pinInput.setHint("Code PIN");
                pinInput.setGravity(Gravity.CENTER);
                pinInput.setSingleLine(true);

                AlertDialog dialog = new AlertDialog.Builder(
                        MainActivity.this)
                        .setTitle("Accès aux paramètres")
                        .setMessage("Entrez votre code PIN.")
                        .setView(pinInput)
                        .setPositiveButton("Valider", null)
                        .setNegativeButton("Annuler", (d, w) ->
                                sendPinResult(false))
                        .create();

                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(v -> {
                                String pin = pinInput.getText()
                                        .toString();

                                if (SETTINGS_PIN.equals(pin)) {
                                    dialog.dismiss();
                                    sendPinResult(true);
                                } else {
                                    pinInput.setText("");
                                    pinInput.setError(
                                            "Code PIN incorrect");
                                }
                            });
                });

                dialog.show();
            });
        }

        private void sendPinResult(boolean valid) {
            webView.evaluateJavascript(
                    "if(window.kemsSettingsPinResult){" +
                    "window.kemsSettingsPinResult(" + valid + ");}",
                    null);
        }

        @JavascriptInterface
        public void saveJson(String json) {
            runOnUiThread(() -> {
                pendingJson = json;

                Intent intent =
                        new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        "KEMS_sauvegarde.json");

                try {
                    startActivityForResult(
                            intent, SAVE_FILE_REQUEST);
                } catch (Exception e) {
                    pendingJson = null;
                    Toast.makeText(context,
                            "Impossible d'ouvrir l'enregistrement.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void printInvoice(String html) {
            runOnUiThread(() -> {
                WebView printView = new WebView(context);
                printView.getSettings().setJavaScriptEnabled(false);

                printView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(
                            WebView view, String url) {

                        PrintManager manager =
                                (PrintManager) getSystemService(
                                        Context.PRINT_SERVICE);

                        String jobName = "KEMS_Facture";

                        manager.print(
                                jobName,
                                view.createPrintDocumentAdapter(jobName),
                                new PrintAttributes.Builder()
                                        .setMediaSize(
                                                PrintAttributes.MediaSize.ISO_A4)
                                        .setResolution(
                                                new PrintAttributes.Resolution(
                                                        "pdf", "PDF", 300, 300))
                                        .setMinMargins(
                                                PrintAttributes.Margins.NO_MARGINS)
                                        .build());
                    }
                });

                printView.loadDataWithBaseURL(
                        null, html, "text/html", "UTF-8", null);
            });
        }
    }
}
