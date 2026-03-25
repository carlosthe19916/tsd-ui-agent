package org.acme.services.git;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@ApplicationScoped
public class GitSslConfig {

    private static final TrustManager[] TRUST_ALL = {new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }};

    @ConfigProperty(name = "quarkus.tls.trust-all", defaultValue = "false")
    boolean trustAll;

    void onStart(@Observes StartupEvent event) {
        if (!trustAll) {
            return;
        }

        var defaultFactory = new JDKHttpConnectionFactory();

        HttpTransport.setConnectionFactory(new HttpConnectionFactory() {
            @Override
            public HttpConnection create(URL url) throws IOException {
                return configureTrustAll(defaultFactory.create(url));
            }

            @Override
            public HttpConnection create(URL url, Proxy proxy) throws IOException {
                return configureTrustAll(defaultFactory.create(url, proxy));
            }
        });
    }

    private static HttpConnection configureTrustAll(HttpConnection conn) {
        try {
            conn.configure(null, TRUST_ALL, null);
            conn.setHostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to configure trust-all SSL for JGit", e);
        }
        return conn;
    }
}
