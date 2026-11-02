package com.kems.facturation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String APP_PIN = "1707";
    private static final int REQUEST_SAVE_JSON = 1001;
    private static final int REQUEST_IMPORT_JSON = 1002;

    private ValueCallback<Uri[]> fileUploadCallback;

    private String pendingJson;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.rgb(15, 35, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 35, 30));

        showStartupPin();
    }

    // PIN demandé une seule fois à l'ouverture
    private void showStartupPin() {

        EditText pinInput = new EditText(this);

        pinInput.setHint("Saisir le code PIN");
        pinInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );
        pinInput.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("KEMS Facturation")
                .setMessage("Veuillez saisir votre code PIN pour continuer.")
                .setView(pinInput)
                .setCancelable(false)
                .setPositiveButton("Valider", null)
                .setNegativeButton("Quitter", (d, which) -> finish())
                .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String enteredPin =
                                pinInput.getText().toString();

                        if (APP_PIN.equals(enteredPin)) {
                            dialog.dismiss();
                            initializeWebView();
                        } else {
                            pinInput.setError("Code PIN incorrect");
                            pinInput.setText("");
                        }
                    });
        });

        dialog.show();
    }

    // Initialisation de l'application après validation du PIN
    private void initializeWebView() {

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            // Confirmation JavaScript : supprimer, vider l'historique...
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
                        .setPositiveButton("Oui", (d, w) ->
                                result.confirm())
                        .setNegativeButton("Annuler", (d, w) ->
                                result.cancel())
                        .setOnCancelListener(d -> result.cancel())
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
                        .setPositiveButton("OK", (d, w) ->
                                result.confirm())
                        .setOnCancelListener(d -> result.confirm())
                        .show();

                return true;
            }

            // Sélection d'un fichier JSON pour l'importation
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {

                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }

                fileUploadCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");

                try {
                    startActivityForResult(intent, REQUEST_IMPORT_JSON);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }

                return true;
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(this),
                "KemsAndroid"
        );

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    // Résultat de la sélection ou de l'enregistrement JSON
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMPORT_JSON) {

            if (fileUploadCallback != null) {

                Uri[] results = null;

                if (resultCode == RESULT_OK &&
                        data != null &&
                        data.getData() != null) {

                    results = new Uri[]{data.getData()};
                }

                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }

        if (requestCode == REQUEST_SAVE_JSON) {

            if (resultCode == RESULT_OK &&
                    data != null &&
                    data.getData() != null &&
                    pendingJson != null) {

                try {
                    android.os.ParcelFileDescriptor pfd =
                            getContentResolver().openFileDescriptor(
                                    data.getData(), "w"
                            );

                    if (pfd != null) {
                        java.io.FileOutputStream output =
                                new java.io.FileOutputStream(
                                        pfd.getFileDescriptor()
                                );

                        output.write(pendingJson.getBytes("UTF-8"));
                        output.close();
                        pfd.close();

                        new AlertDialog.Builder(this)
                                .setMessage("Sauvegarde JSON enregistrée.")
                                .setPositiveButton("OK", null)
                                .show();
                    }

                } catch (Exception e) {

                    new AlertDialog.Builder(this)
                            .setMessage("Erreur pendant la sauvegarde JSON.")
                            .setPositiveButton("OK", null)
                            .show();
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

        // Sauvegarde JSON
        @JavascriptInterface
        public void saveJson(String json) {

            runOnUiThread(() -> {

                pendingJson = json;

                Intent intent = new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        "KEMS_Facturation_Sauvegarde.json"
                );

                startActivityForResult(
                        intent,
                        REQUEST_SAVE_JSON
                );
            });
        }

        // Impression PDF
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
