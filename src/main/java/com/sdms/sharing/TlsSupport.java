package com.sdms.sharing;

import com.sdms.util.AppPaths;
import java.math.BigInteger;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Encrypted LAN traffic with a persisted certificate pin after initial pairing. */
public final class TlsSupport {
  public record Identity(SSLContext context, String fingerprint) {}

  private TlsSupport() {}

  public static Identity server() throws Exception {
    Path storePath = AppPaths.appDir().resolve("sharing-tls.p12");
    char[] password = "AthleteTrack-local-key-store".toCharArray();
    KeyStore store = KeyStore.getInstance("PKCS12");
    if (Files.isRegularFile(storePath)) {
      try (var input = Files.newInputStream(storePath)) {
        store.load(input, password);
      }
    } else {
      store.load(null, password);
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      KeyPair pair = generator.generateKeyPair();
      X500Name subject = new X500Name("CN=Athlete Track Main Computer");
      Instant now = Instant.now();
      var builder =
          new JcaX509v3CertificateBuilder(
              subject,
              new BigInteger(120, new SecureRandom()),
              Date.from(now.minusSeconds(300)),
              Date.from(now.plus(Duration.ofDays(3650))),
              subject,
              pair.getPublic());
      Set<String> ips = new HashSet<>(List.of("127.0.0.1", SharingManager.primaryLocalAddress()));
      var interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        var addresses = interfaces.nextElement().getInetAddresses();
        while (addresses.hasMoreElements()) {
          var address = addresses.nextElement();
          if (address instanceof Inet4Address) ips.add(address.getHostAddress());
        }
      }
      List<GeneralName> names = new ArrayList<>();
      for (String ip : ips) names.add(new GeneralName(GeneralName.iPAddress, ip));
      names.add(new GeneralName(GeneralName.dNSName, "localhost"));
      names.add(new GeneralName(GeneralName.dNSName, InetAddress.getLocalHost().getHostName()));
      builder.addExtension(
          Extension.subjectAlternativeName,
          false,
          new GeneralNames(names.toArray(GeneralName[]::new)));
      builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
      X509Certificate certificate =
          new JcaX509CertificateConverter()
              .getCertificate(
                  builder.build(
                      new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
      store.setKeyEntry(
          "host", pair.getPrivate(), password, new java.security.cert.Certificate[] {certificate});
      try (var output = Files.newOutputStream(storePath)) {
        store.store(output, password);
      }
    }
    KeyManagerFactory managers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(store, password);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(managers.getKeyManagers(), null, new SecureRandom());
    return new Identity(context, fingerprint((X509Certificate) store.getCertificate("host")));
  }

  public static HttpClient client(String pin) {
    try {
      X509ExtendedTrustManager trust =
          new X509ExtendedTrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String auth)
                throws java.security.cert.CertificateException {
              throw new java.security.cert.CertificateException("Client certificate unsupported");
            }

            public void checkServerTrusted(X509Certificate[] chain, String auth)
                throws java.security.cert.CertificateException {
              if (chain.length == 0)
                throw new java.security.cert.CertificateException("Missing host certificate");
              chain[0].checkValidity();
              if (pin != null
                  && !pin.isBlank()
                  && !MessageDigest.isEqual(
                      pin.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                      fingerprint(chain[0]).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new java.security.cert.CertificateException(
                    "Main computer identity changed. Pair again with the administrator.");
            }

            // A pinned certificate identifies the computer even when its DHCP address changes.
            public void checkServerTrusted(X509Certificate[] chain, String auth, Socket socket)
                throws java.security.cert.CertificateException {
              checkServerTrusted(chain, auth);
            }

            public void checkServerTrusted(X509Certificate[] chain, String auth, SSLEngine engine)
                throws java.security.cert.CertificateException {
              checkServerTrusted(chain, auth);
            }

            public void checkClientTrusted(X509Certificate[] chain, String auth, Socket socket)
                throws java.security.cert.CertificateException {
              checkClientTrusted(chain, auth);
            }

            public void checkClientTrusted(X509Certificate[] chain, String auth, SSLEngine engine)
                throws java.security.cert.CertificateException {
              checkClientTrusted(chain, auth);
            }
          };
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {trust}, new SecureRandom());
      return HttpClient.newBuilder()
          .sslContext(context)
          .connectTimeout(Duration.ofSeconds(6))
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot initialize encrypted sharing", e);
    }
  }

  public static String fingerprint(X509Certificate certificate)
      throws java.security.cert.CertificateException {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
