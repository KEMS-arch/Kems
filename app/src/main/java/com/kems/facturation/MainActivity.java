
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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String APP_PIN = "1707";

    private static final String SERVER_URL =
            "http://100.95.210.16:3000";

    private static final String KEY_ALIAS = "KEMS_SYNC_KEY";
    private static final String PREFS = "kems_secure";
    private static final String TOKEN_KEY = "server_token";

    private static final int REQUEST_SAVE_JSON = 1001;
    private static final int REQUEST_IMPORT_JSON = 1002;

    private ValueCallback<Uri[]> fileUploadCallback;
    private String pendingJson;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(Color.rgb(15, 35, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 35, 30));

        showStartupPin();
    }

    // PIN DE DEMARRAGE

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

        dialog.setOnShowListener(d ->
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
                        })
        );

        dialog.show();
    }

    // INITIALISATION WEBVIEW

    private void initializeWebView() {

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

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
                    startActivityForResult(
                            intent,
                            REQUEST_IMPORT_JSON
                    );

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

        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    // SECURITE DE LA CLE

    private SecretKey getEncryptionKey() throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance("AndroidKeyStore");

        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        generator.init(
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                        KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build()
        );

        return generator.generateKey();
    }

    private void saveEncryptedToken(String token) throws Exception {

        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getEncryptionKey()
        );

        byte[] encrypted = cipher.doFinal(
                token.getBytes(StandardCharsets.UTF_8)
        );

        byte[] iv = cipher.getIV();

        byte[] combined = new byte[iv.length + encrypted.length];

        System.arraycopy(
                iv, 0,
                combined, 0,
                iv.length
        );

        System.arraycopy(
                encrypted, 0,
                combined, iv.length,
                encrypted.length
        );

        String encoded = Base64.encodeToString(
                combined,
                Base64.NO_WRAP
        );

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, encoded)
                .apply();
    }

    private String getDecryptedToken() throws Exception {

        String encoded = getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        ).getString(TOKEN_KEY, null);

        if (encoded == null || encoded.isEmpty()) {
            throw new Exception(
                    "Clé du serveur non configurée."
            );
        }

        byte[] combined = Base64.decode(
                encoded,
                Base64.NO_WRAP
        );

        if (combined.length <= 12) {
            throw new Exception(
                    "Clé enregistrée invalide."
            );
        }

        byte[] iv = new byte[12];

        byte[] encrypted =
                new byte[combined.length - 12];

        System.arraycopy(
                combined, 0,
                iv, 0,
                12
        );

        System.arraycopy(
                combined, 12,
                encrypted, 0,
                encrypted.length
        );

        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.DECRYPT_MODE,
                getEncryptionKey(),
                new GCMParameterSpec(128, iv)
        );

        return new String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
        );
    }

    // COMMUNICATION AVEC LE SERVEUR

    private String serverRequest(
            String endpoint,
            String method,
            String body,
            boolean authenticated
    ) throws Exception {

        HttpURLConnection connection = null;

        try {

            URL url = new URL(
                    SERVER_URL + endpoint
            );

            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod(method);

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            if (authenticated) {

                connection.setRequestProperty(
                        "x-kems-token",
                        getDecryptedToken()
                );
            }

            if (body != null) {

                connection.setDoOutput(true);

                try (OutputStream output =
                             connection.getOutputStream()) {

                    output.write(
                            body.getBytes(StandardCharsets.UTF_8)
                    );
                }
            }

            int status = connection.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            StringBuilder response =
                    new StringBuilder();

            if (stream != null) {

                try (BufferedReader reader =
                             new BufferedReader(
                                     new InputStreamReader(
                                             stream,
                                             StandardCharsets.UTF_8
                                     )
                             )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            if (status < 200 || status >= 300) {

                throw new Exception(
                        "Erreur serveur HTTP " +
                        status + " : " + response
                );
            }

            return response.toString();

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // RETOUR VERS JAVASCRIPT

    private void sendResult(
            String action,
            boolean success,
            String message
    ) {

        runOnUiThread(() -> {

            if (webView == null) return;

            String script =
                    "if(window.onKemsSyncResult){" +
                    "window.onKemsSyncResult(" +
                    JSONObject.quote(action) + "," +
                    success + "," +
                    JSONObject.quote(message) +
                    ");}";

            webView.evaluateJavascript(
                    script,
                    null
            );
        });
    }

    // FICHIERS JSON

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_IMPORT_JSON) {

            if (fileUploadCallback != null) {

                Uri[] results = null;

                if (resultCode == RESULT_OK &&
                        data != null &&
                        data.getData() != null) {

                    results = new Uri[]{
                            data.getData()
                    };
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
                                    data.getData(),
                                    "w"
                            );

                    if (pfd != null) {

                        java.io.FileOutputStream output =
                                new java.io.FileOutputStream(
                                        pfd.getFileDescriptor()
                                );

                        output.write(
                                pendingJson.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

                        output.close();
                        pfd.close();

                        new AlertDialog.Builder(this)
                                .setMessage(
                                        "Sauvegarde JSON enregistrée."
                                )
                                .setPositiveButton("OK", null)
                                .show();
                    }

                } catch (Exception e) {

                    new AlertDialog.Builder(this)
                            .setMessage(
                                    "Erreur pendant la sauvegarde JSON."
                            )
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

    // PONT ANDROID / JAVASCRIPT

    public class AndroidBridge {

        private final Context context;

        AndroidBridge(Context c) {
            context = c;
        }

        // SAUVEGARDE JSON

        @JavascriptInterface
        public void saveJson(String json) {

            runOnUiThread(() -> {

                pendingJson = json;

                Intent intent = new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

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

        // IMPRESSION FACTURE

        @JavascriptInterface
        public void printInvoice(String html) {

            runOnUiThread(() -> {

                WebView printView =
                        new WebView(context);

                printView.getSettings()
                        .setJavaScriptEnabled(false);

                printView.setWebViewClient(
                        new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        PrintManager manager =
                                (PrintManager) getSystemService(
                                        Context.PRINT_SERVICE
                                );

                        String jobName = "KEMS_Facture";

                        manager.print(
                                jobName,
                                view.createPrintDocumentAdapter(
                                        jobName
                                ),
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

        // ENREGISTRER LA CLE DU SERVEUR

        @JavascriptInterface
        public void saveSyncToken(String token) {

            executor.execute(() -> {

                try {

                    if (token == null ||
                            token.trim().isEmpty()) {

                        throw new Exception(
                                "Veuillez saisir la clé."
                        );
                    }

                    saveEncryptedToken(
                            token.trim()
                    );

                    sendResult(
                            "saveToken",
                            true,
                            "Clé enregistrée de manière sécurisée."
                    );

                } catch (Exception e) {

                    sendResult(
                            "saveToken",
                            false,
                            e.getMessage()
                    );
                }
            });
        }

        // TEST CONNEXION SERVEUR

        @JavascriptInterface
        public void checkSyncServer() {

            executor.execute(() -> {

                try {

                    String response = serverRequest(
                            "/api/health",
                            "GET",
                            null,
                            false
                    );

                    sendResult(
                            "health",
                            true,
                            response
                    );

                } catch (Exception e) {

                    sendResult(
                            "health",
                            false,
                            e.getMessage()
                    );
                }
            });
        }

        // NOUVEAU : RECUPERER LA BASE PRIX

        @JavascriptInterface
        public void getPriceCatalog() {

            executor.execute(() -> {

                try {

                    String response = serverRequest(
                            "/api/prix",
                            "GET",
                            null,
                            true
                    );

                    sendResult(
                            "prices",
                            true,
                            response
                    );

                } catch (Exception e) {

                    sendResult(
                            "prices",
                            false,
                            e.getMessage()
                    );
                }
            });
        }

        // TELECHARGER LES DONNEES

        @JavascriptInterface
        public void downloadSyncData() {

            executor.execute(() -> {

                try {

                    String response = serverRequest(
                            "/api/data",
                            "GET",
                            null,
                            true
                    );

                    sendResult(
                            "download",
                            true,
                            response
                    );

                } catch (Exception e) {

                    sendResult(
                            "download",
                            false,
                            e.getMessage()
                    );
                }
            });
        }

        // ENVOYER LES DONNEES

        @JavascriptInterface
        public void uploadSyncData(String json) {

            executor.execute(() -> {

                try {

                    JSONObject data =
                            new JSONObject(json);

                    if (!(data.opt("cfg") instanceof JSONObject) ||
                            !(data.opt("invoices") instanceof JSONArray)) {

                        throw new Exception(
                                "Format des données invalide."
                        );
                    }

                    String response = serverRequest(
                            "/api/data",
                            "PUT",
                            data.toString(),
                            true
                    );

                    sendResult(
                            "upload",
                            true,
                            response
                    );

                } catch (Exception e) {

                    sendResult(
                            "upload",
                            false,
                            e.getMessage()
                    );
                }
            });
        }
    }
}
