package android.net;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;
import android.util.Log;
import android.util.NtpTrustedTime.TimeResult;

import com.android.internal.R;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import libcore.io.IoUtils;

import static com.android.net.module.util.ConnectivityUtils.saturatedCast;

/** @hide */
public class HttpsTimeClient {
    private static final String TAG = HttpsTimeClient.class.getSimpleName();

    private final Config config;
    private final Network network;

    public HttpsTimeClient(Config config, Network network) {
        this.config = config;
        this.network = network;
    }

    public static class Config {
        public final List<URL> urls;
        public final int timeoutMillis;

        Config(List<URL> urls, int timeoutMillis) {
            this.urls = urls;
            this.timeoutMillis = timeoutMillis;
        }

        public static Config getDefault(Context ctx) {
            Resources res = ctx.getResources();

            String[] urlArr = res.getStringArray(R.array.config_httpsTimeUrls);
            int num = urlArr.length;
            List<URL> urls = new ArrayList<>(num);
            for (int i = 0; i < num; ++i) {
                URL url;
                try {
                    url = new URL(urlArr[i]);
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
                urls.add(url);
            }
            final int timeoutMillis = res.getInteger(R.integer.config_ntpTimeout);

            return new Config(urls, timeoutMillis);
        }

        @Override
        public String toString() {
            return "HttpsTimeConfig{urls=" + Arrays.toString(urls.toArray())
                    + ", timeoutMillis=" + timeoutMillis
                    + '}';
        }
    }

    public static class Result {
        public final TimeResult timeResult;
        public final URL url;

        Result(TimeResult timeResult, URL url) {
            this.timeResult = timeResult;
            this.url = url;
        }
    }

    public Result requestTime(@Nullable URL lastSuccessfulUrl) {
        ArrayList<URL> urls = new ArrayList<>(config.urls);
        if (urls.remove(lastSuccessfulUrl)) {
            urls.add(0, lastSuccessfulUrl);
        }

        for (URL url : urls) {
            TimeResult timeResult = requestTimeInner(url);
            if (timeResult != null) {
                return new Result(timeResult, url);
            }
        }
        return null;
    }

    private TimeResult requestTimeInner(URL url) {
        final Network networkForResolv = network.getPrivateDnsBypassingCopy();
        final int timeout = config.timeoutMillis;
        HttpsURLConnection conn = null;
        InputStream streamToClose = null;
        try {
            SSLSocketFactory socketFactory = createSSLSocketFactory();

            // establish HTTPS connection in advance to improve accuracy
            conn = (HttpsURLConnection) networkForResolv.openConnection(url);
            conn.setSSLSocketFactory(socketFactory);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            // closing the InputStream makes the connection available for reuse
            conn.getInputStream().close();

            conn = (HttpsURLConnection) networkForResolv.openConnection(url);
            conn.setSSLSocketFactory(socketFactory);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("Connection", "close");

            final long requestTime = System.currentTimeMillis();
            final long requestTicks = SystemClock.elapsedRealtime();
            streamToClose = conn.getInputStream();
            final long responseTicks = SystemClock.elapsedRealtime();

            long serverTime;
            try {
                serverTime = Long.parseLong(conn.getHeaderField("X-Time"));
            } catch (final NumberFormatException e) {
                Log.w(TAG, "X-Time header is missing, falling back to the less precise date header", e);
                serverTime = conn.getDate();
            }

            final long roundTripTime = responseTicks - requestTicks;
            final long responseTime = requestTime + roundTripTime;
            final long clockOffset = ((serverTime - requestTime) + (serverTime - responseTime)) / 2;

            Log.d(TAG, "roundTripTime: " + roundTripTime + " ms, "
                    + "clockOffset: " + clockOffset + " ms");

            if (serverTime < android.os.Build.TIME) {
                throw new GeneralSecurityException("server timestamp is before android.os.Build.TIME");
            }

            EventLogTags.writeHttpsTimeSuccess(url.toString(), roundTripTime, clockOffset);

            return new TimeResult(
                    responseTime + clockOffset, // unixEpochTimeMillis
                    responseTicks, // elapsedRealtimeMillis
                    saturatedCast(roundTripTime / 2), // uncertaintyMillis
                    InetSocketAddress.createUnresolved(url.getHost(), 443) // ntpServerSocketAddress
            );
        } catch (Exception e) {
            EventLogTags.writeHttpsTimeFailure(url.toString(), e.toString());
            Log.e(TAG, "request failed, url: " + url, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (streamToClose != null) {
                // it's important to close the stream after conn.disconnect(), otherwise connection
                // might be reused for the next request
                IoUtils.closeQuietly(streamToClose);
            }
        }
    }

    // SSL certificate time checks might fail if the time was never synced before, or if it has
    // drifted far enough after the previous sync,
    //
    // To prevent this issue, construct a special SSLSocketFactory that uses the OS build time
    // (android.os.Build.TIME) for SSL certificate expiration checks
    private static SSLSocketFactory createSSLSocketFactory() throws KeyManagementException,
            KeyStoreException, NoSuchAlgorithmException {
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        TrustManager[] trustManagers = tmf.getTrustManagers();
        for (int i = 0; i < trustManagers.length; ++i) {
            if (trustManagers[i] instanceof X509TrustManager xtm) {
                trustManagers[i] = new X509TrustManagerWrapper(xtm);
            }
        }

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, trustManagers, null);
        return sslCtx.getSocketFactory();
    }

    // see createSSLSocketFactory()
    static class X509TrustManagerWrapper implements X509TrustManager {
        final X509TrustManager orig;

        X509TrustManagerWrapper(X509TrustManager orig) {
            this.orig = orig;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            int num = chain.length;
            var wrappedChain = new X509Certificate[num];
            for (int i = 0; i < num; ++i) {
                wrappedChain[i] = new X509CertificateWrapper(chain[i]);
            }
            orig.checkServerTrusted(wrappedChain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return orig.getAcceptedIssuers();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // unused
            throw new IllegalStateException(authType);
        }
    }

    // see createSSLSocketFactory()
    static class X509CertificateWrapper extends X509Certificate {
        private final X509Certificate orig;

        X509CertificateWrapper(X509Certificate orig) {
            this.orig = orig;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException {
            checkValidity(null);
        }

        @Override
        public void checkValidity(Date ignored) throws CertificateExpiredException {
            final Date buildDate = new Date(android.os.Build.TIME);

            // don't check notBefore: HttpsTimeClient is the main time source

            if (buildDate.after(getNotAfter())) {
                String msg = "notAfter: "  + getNotAfter() + ", buildDate: " + buildDate;
                throw new CertificateExpiredException(msg);
            }
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return orig.getCriticalExtensionOIDs();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return orig.getExtensionValue(oid);
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return orig.getNonCriticalExtensionOIDs();
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return orig.hasUnsupportedCriticalExtension();
        }

        @Override
        public int getBasicConstraints() {
            return orig.getBasicConstraints();
        }

        @Override
        public Principal getIssuerDN() {
            return orig.getIssuerDN();
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return orig.getIssuerUniqueID();
        }

        @Override
        public boolean[] getKeyUsage() {
            return orig.getKeyUsage();
        }

        @Override
        public Date getNotAfter() {
            return orig.getNotAfter();
        }

        @Override
        public Date getNotBefore() {
            return orig.getNotBefore();
        }

        @Override
        public BigInteger getSerialNumber() {
            return orig.getSerialNumber();
        }

        @Override
        public String getSigAlgName() {
            return orig.getSigAlgName();
        }

        @Override
        public String getSigAlgOID() {
            return orig.getSigAlgOID();
        }

        @Override
        public byte[] getSigAlgParams() {
            return orig.getSigAlgParams();
        }

        @Override
        public byte[] getSignature() {
            return orig.getSignature();
        }

        @Override
        public Principal getSubjectDN() {
            return orig.getSubjectDN();
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return orig.getSubjectUniqueID();
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return orig.getTBSCertificate();
        }

        @Override
        public int getVersion() {
            return orig.getVersion();
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return orig.getEncoded();
        }

        @Override
        public PublicKey getPublicKey() {
            return orig.getPublicKey();
        }

        @Override
        public String toString() {
            return orig.toString();
        }

        @Override
        public void verify(PublicKey key) throws CertificateException, InvalidKeyException,
                NoSuchAlgorithmException, NoSuchProviderException, SignatureException {
            orig.verify(key);
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException,
                InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException,
                SignatureException {
            orig.verify(key, sigProvider);
        }

        @Override
        public List<String> getExtendedKeyUsage() throws CertificateParsingException {
            return orig.getExtendedKeyUsage();
        }

        @Override
        public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
            return orig.getIssuerAlternativeNames();
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return orig.getIssuerX500Principal();
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
            return orig.getSubjectAlternativeNames();
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return orig.getSubjectX500Principal();
        }
    }
}
