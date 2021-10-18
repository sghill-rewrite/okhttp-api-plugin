package io.jenkins.jenkins.plugins.okhttp.api;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This class allows to take into consideration if Jenkins is running behind a proxy or not and return a proper {@link OkHttpClient}
 * client.
 */
public class JenkinsOkHttpClient {

    private static final Logger LOGGER = Logger.getLogger(JenkinsOkHttpClient.class.getName());

    private JenkinsOkHttpClient() {
    }

    /**
     * Generates a new builder for the given base client.
     * It applies the current {@link Jenkins#proxy} configuration.
     *
     * @return a builder to configure the client
     */
    public static OkHttpClient.Builder newClientBuilder(OkHttpClient httpClient) {
        OkHttpClient.Builder reBuild = httpClient.newBuilder();
        if (Jenkins.get().proxy != null) {
            final ProxyConfiguration proxy = Jenkins.get().proxy;
            Proxy proxyServer = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.name, proxy.port));
            if (proxy.getUserName() != null) {
                Authenticator proxyAuthenticator = new Authenticator() {
                    @Override public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(proxy.getUserName(), proxy.getPassword());
                        return response.request().newBuilder()
                                       .header("Proxy-Authorization", credential)
                                       .build();
                    }
                };
                reBuild.proxyAuthenticator(proxyAuthenticator);
            }

            // TODO: https://github.com/square/okhttp/issues/3787
            // not sure if this is correct, needs manual testing
            if (proxy.name.startsWith("https")) {
                reBuild.socketFactory(SSLSocketFactory.getDefault());
            }
            ProxySelector proxySelector = new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    final List<Proxy> proxies = new ArrayList<>(1);
                    String host = uri.getHost();
                    for (Pattern p : proxy.getNoProxyHostPatterns()) {
                        if (p.matcher(host).matches()) {
                            proxies.add(Proxy.NO_PROXY);
                            return proxies;
                        }
                    }
                    proxies.add(proxyServer);
                    return proxies;
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    LOGGER.log(Level.WARNING, "Proxy connection failed", ioe);
                }
            };
            reBuild.proxySelector(proxySelector);
        } else {
            reBuild.proxy(Proxy.NO_PROXY);
        }

        return reBuild;
    }
}
