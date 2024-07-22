package com.hmdm.launcher.helper;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.util.LegacyUtils;
import com.hmdm.launcher.util.RemoteLogger;
import com.hmdm.launcher.util.Utils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean installCertificate(Context context, String cert, String path) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
            boolean res = dpm.installCaCert(adminComponentName, cert.getBytes());
            if (path != null) {
                if (res) {
                    RemoteLogger.log(context, Const.LOG_INFO, "Certificate installed: " + path);
                } else {
                    RemoteLogger.log(context, Const.LOG_WARN, "Failed to install certificate " + path);
                }
            }
            return res;
        } catch (Exception e) {
            if (path != null) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to install certificate " + path + ": " + e.getMessage());
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
            installCertificate(context, cert.cert, null);
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
            installCertificate(context, cert.cert, cert.path);
        }
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
