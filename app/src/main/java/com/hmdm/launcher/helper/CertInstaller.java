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
    public static List<String> getCertificates(Context context) {
        String[] names = context.getResources().getStringArray(R.array.certificates);
        if (names == null) {
            return null;
        }
        List<String> result = new LinkedList<>();
        for (String name : names) {
            try {
                String cert = Utils.loadStreamAsString(new InputStreamReader(context.getAssets().open(name)));
                if (cert != null) {
                    result.add(cert);
                } else {
                    Log.e(Const.LOG_TAG, "Failed to read certificate " + name);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean installCertificate(Context context, String cert) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponentName = LegacyUtils.getAdminComponentName(context);
            boolean res = dpm.installCaCert(adminComponentName, cert.getBytes());
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void installCertificatesFromAssets(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        List<String> certs = getCertificates(context);
        if (certs == null || certs.size() == 0) {
            return;
        }
        for (String cert : certs) {
            installCertificate(context, cert);
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
