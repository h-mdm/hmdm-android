package com.hmdm.launcher.helper;

import static com.hmdm.launcher.util.InstallUtils.DO_NOT_VERIFY;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.util.InstallUtils;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class CertInstaller {
    static class CertEntry {
        public String path;
        public String cert;
        public CertEntry() {}
        public CertEntry(String path, String cert) {
            this.path = path;
            this.cert = cert;
        }
    }

    static class ClientCertEntry {
        public String path;
        public String password;
        public ClientCertEntry() {}
        public ClientCertEntry(String path, String password) {
            this.path = path;
            this.password = password;
        }
    }

    public interface Listener {
        void onCertInstallSuccess();
        void onCertInstallError(String url, String message);
    }

    public static List<CertEntry> getCertificatesFromAssets(Context context) {
        String[] names = context.getResources().getStringArray(R.array.certificates);
        if (names == null) {
            return null;
        }
        List<CertEntry> result = new LinkedList<>();
        for (String name : names) {
            try {
                String cert = Utils.loadStreamAsString(new InputStreamReader(context.getAssets().open(name)));
                if (cert != null) {
                    result.add(new CertEntry(name, cert));
                } else {
                    Log.e(Const.LOG_TAG, "Failed to read certificate " + name);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static List<CertEntry> getCertificatesFromFiles(Context context, String paths) {
        String[] names = paths.split("[;:,]");
        if (names == null) {
            return null;
        }
        List<CertEntry> result = new LinkedList<>();
        for (String name : names) {
            String adjustedName = name;
            if (!adjustedName.startsWith("/storage/emulated/0/")) {
                if (!adjustedName.startsWith("/")) {
                    adjustedName = "/" + adjustedName;
                }
                adjustedName = "/storage/emulated/0" + adjustedName;
            }
            try {
                String cert = Utils.loadFileAsString(adjustedName);
                if (cert != null) {
                    result.add(new CertEntry(adjustedName, cert));
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to read certificate " + adjustedName);
                }
            } catch (IOException e) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to read certificate " + adjustedName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return result;
    }

    public static List<ClientCertEntry> getClientCertificatesFromFiles(Context context, String paths) {
        String[] names = paths.split(",");
        List<ClientCertEntry> result = new LinkedList<>();
        for (String name : names) {
            String adjustedName = name;
            if (!adjustedName.startsWith("/storage/emulated/0/")) {
                if (!adjustedName.startsWith("/")) {
                    adjustedName = "/" + adjustedName;
                }
                adjustedName = "/storage/emulated/0" + adjustedName;
            }
            // Extract password from path:password format (password after the last colon)
            String password = "";
            String certPath = adjustedName;
            int lastColonIndex = adjustedName.lastIndexOf(':');
            if (lastColonIndex > adjustedName.lastIndexOf('/')) {
                certPath = adjustedName.substring(0, lastColonIndex);
                password = adjustedName.substring(lastColonIndex + 1);
            }
            File certFile = new File(certPath);
            if (!certFile.exists()) {
                RemoteLogger.log(context, Const.LOG_WARN, "Client certificate file not found: " + certPath);
                continue;
            }
            result.add(new ClientCertEntry(certPath, password));
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean installCertificate(Context context, String cert, String path, boolean remoteLog) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
            boolean res = dpm.installCaCert(adminComponentName, cert.getBytes());
            if (remoteLog) {
                if (res) {
                    RemoteLogger.log(context, Const.LOG_INFO, "Certificate installed: " + path);
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to install certificate " + path);
                }
            } else {
                if (res) {
                    Log.d(Const.LOG_TAG, "Certificate installed: " + path);
                } else {
                    Log.w(Const.LOG_TAG, "Failed to install certificate: " + path);
                }
            }
            return res;
        } catch (Exception e) {
            if (remoteLog) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to install certificate " + path + ": " + e.getMessage());
            } else {
                Log.w(Const.LOG_TAG, "Failed to install certificate " + path + ": " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean installClientCertificate(Context context, String path, String password, boolean remoteLog) {
        try {
            File certFile = new File(path);
            byte[] pkcs12Data = Files.readAllBytes(certFile.toPath());
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pkcs12Data), password.isEmpty() ? null : password.toCharArray());
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isKeyEntry(alias)) {
                    Certificate[] chain = ks.getCertificateChain(alias);
                    if (chain != null && chain.length > 0) {
                        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password.isEmpty() ? null : password.toCharArray());
                        if (privateKey != null) {
                            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
                            boolean res = dpm.installKeyPair(adminComponentName, privateKey, chain, alias, false);
                            if (remoteLog) {
                                if (res) {
                                    RemoteLogger.log(context, Const.LOG_INFO, "Client certificate installed: " + path);
                                } else {
                                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to install client certificate: " + path);
                                }
                            } else {
                                if (res) {
                                    Log.d(Const.LOG_TAG, "Client certificate installed: " + path);
                                } else {
                                    Log.w(Const.LOG_TAG, "Failed to install client certificate: " + path);
                                }
                            }
                            return res;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            if (remoteLog) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to install client certificate " + path + ": " + e.getMessage());
            } else {
                Log.w(Const.LOG_TAG, "Failed to install client certificate " + path + ": " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    public static void installCertificatesFromAssets(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        List<CertEntry> certs = getCertificatesFromAssets(context);
        if (certs == null || certs.size() == 0) {
            return;
        }
        for (CertEntry cert : certs) {
            // Do not log installation of certificates from assets
            // because the remote logger is not yet initialized
            installCertificate(context, cert.cert, cert.path, false);
        }
    }

    public static void installCertificatesFromFiles(Context context, String paths) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        List<CertEntry> certs = getCertificatesFromFiles(context, paths);
        if (certs == null || certs.size() == 0) {
            return;
        }
        for (CertEntry cert : certs) {
            installCertificate(context, cert.cert, cert.path, true);
        }
    }

    public static void installClientCertificatesFromFiles(Context context, String paths) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        List<ClientCertEntry> certs = getClientCertificatesFromFiles(context, paths);
        if (certs == null || certs.size() == 0) {
            return;
        }
        for (ClientCertEntry cert : certs) {
            installClientCertificate(context, cert.path, cert.password, true);
        }
    }

    public static void downloadAndInstallCerts(Context context, String urls, Listener listener) {
        String[] urlArray = urls.split(",");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
                urlArray == null || urlArray.length == 0) {
            listener.onCertInstallSuccess();
            return;
        }

        Handler ui = new Handler(Looper.getMainLooper());
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            for (String url : urlArray) {
                try {
                    File certFile = downloadCert(context, url);
                    String cert = Utils.loadFileAsString(certFile.getCanonicalPath());
                    if (cert != null) {
                        // Do not log installation of certificates from assets
                        // because the remote logger is not yet initialized
                        boolean installed = installCertificate(context, cert, certFile.getCanonicalPath(), false);
                        if (!installed) {
                            ui.post(() -> listener.onCertInstallError(url, "Installation error"));
                            // Fail on first error, don't attempt to install more certs
                            return;
                        }
                    } else {
                        ui.post(() -> listener.onCertInstallError(url, "Cannot load file contents"));
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    ui.post(() -> listener.onCertInstallError(url, e.getMessage()));
                    return;
                }
            }
            ui.post(() -> listener.onCertInstallSuccess());
        });
    }

    private static File downloadCert(Context context, String strUrl) throws Exception {
        File tempFile = new File(context.getExternalFilesDir(null), getFileName(strUrl));
        if (tempFile.exists()) {
            tempFile.delete();
        }

        try {
            try {
                if (!tempFile.createNewFile()) {
                    throw new Exception("File " + tempFile.getAbsolutePath() + " can't be created!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                tempFile = File.createTempFile(getFileName(strUrl), ".pem");
            }

            URL url = new URL(strUrl);

            HttpURLConnection connection;
            // Turn off certificate validity check because we didn't yet install trusted certs
            if (url.getProtocol().toLowerCase().equals("https")) {
                connection = (HttpsURLConnection) url.openConnection();
                ((HttpsURLConnection) connection).setHostnameVerifier(DO_NOT_VERIFY);
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setConnectTimeout((int) Const.CONNECTION_TIMEOUT);
            connection.setReadTimeout((int) Const.CONNECTION_TIMEOUT);
            String signature = InstallUtils.getRequestSignature(strUrl);
            if (signature != null) {
                connection.setRequestProperty("X-Request-Signature", signature);
            }
            connection.connect();

            if (connection.getResponseCode() != 200) {
                throw new Exception("Bad server response for " + strUrl + ": " + connection.getResponseCode());
            }

            InputStream is = connection.getInputStream();
            DataInputStream dis = new DataInputStream(is);

            byte[] buffer = new byte[1024];
            int length;

            FileOutputStream fos = new FileOutputStream(tempFile);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.flush();
            fos.close();

            dis.close();
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        }

        return tempFile;
    }


    private static String getFileName(String strUrl) {
        int slashIndex = strUrl.lastIndexOf("/");
        return slashIndex >= 0 ? strUrl.substring(slashIndex) : strUrl;
    }

    public static List<String> getInstalledCerts() {
        try {
            List<String> result = new LinkedList<>();
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");

            if (ks != null) {
                ks.load(null, null);
                Enumeration<String> aliases = ks.aliases();

                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) ks.getCertificate(alias);
                    result.add(cert.getIssuerDN().getName());
                }

                return result;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (java.security.cert.CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
