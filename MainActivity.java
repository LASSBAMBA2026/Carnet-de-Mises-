package com.lassebamba.carnetdemises;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOTE = "https://carnet.local/";
    private static final int CODE_FICHIER = 41;
    private WebView web;
    private ValueCallback<Uri[]> choix;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Pont(), "AndroidSave");
        web.addJavascriptInterface(new Impression(), "AndroidPrint");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.equals(HOTE) || u.equals(HOTE + "index.html")) {
                    try {
                        InputStream in = getAssets().open("index.html");
                        return new WebResourceResponse("text/html", "UTF-8", in);
                    } catch (Exception e) { return null; }
                }
                if (u.startsWith(HOTE)) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
                }
                return null;
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !r.getUrl().toString().startsWith(HOTE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (choix != null) choix.onReceiveValue(null);
                choix = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir une sauvegarde"), CODE_FICHIER);
                } catch (Exception e) {
                    choix = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        if (b == null) web.loadUrl(HOTE + "index.html"); else web.restoreState(b);
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        web.saveState(o);
    }

    @Override
    protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code == CODE_FICHIER && choix != null) {
            Uri[] r = null;
            if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
            choix.onReceiveValue(r);
            choix = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    private class Pont {
        @JavascriptInterface
        public boolean savePdf(String nom, String b64) {
            try {
                nom = nom.replaceAll("[^A-Za-z0-9._-]", "_");
                final byte[] octets = Base64.decode(b64, Base64.DEFAULT);
                Uri u = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, nom);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) return false;
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(octets); }
                } else {
                    File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    d.mkdirs();
                    try (FileOutputStream o = new FileOutputStream(new File(d, nom))) { o.write(octets); }
                }
                if (u != null) {
                    final Uri fu = u;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setDataAndType(fu, "application/pdf");
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(i);
                            } catch (Exception e) { /* aucune application PDF : le fichier est dans Téléchargements */ }
                        }
                    });
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        @JavascriptInterface
        public boolean save(String nom, String texte) {
            try {
                nom = nom.replaceAll("[^A-Za-z0-9._-]", "_");
                byte[] octets = texte.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, nom);
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) return false;
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(octets); }
                } else {
                    File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    d.mkdirs();
                    try (FileOutputStream o = new FileOutputStream(new File(d, nom))) { o.write(octets); }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private class Impression {
        @JavascriptInterface
        public void print() {
            runOnUiThread(new Runnable() {
                public void run() {
                    PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                    PrintDocumentAdapter a = web.createPrintDocumentAdapter("Carnet-de-Mises");
                    pm.print("Carnet de Mises", a, new PrintAttributes.Builder().build());
                }
            });
        }
    }
}
