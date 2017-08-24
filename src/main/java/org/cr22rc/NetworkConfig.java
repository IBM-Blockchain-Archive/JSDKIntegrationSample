package org.cr22rc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;

import static java.lang.String.format;

class NetworkConfig {

    private final JsonObject value;

    NetworkConfig(JsonObject jobj) {
        value = jobj;
    }

    Map<String, OrganizationConfig> orgs;

    OrganizationConfig getOrganization(String name) {
        return getOrganizations().get(name);
    }

    Map<String, OrganizationConfig> getOrganizations() {
        if (orgs == null) {
            orgs = new HashMap<>();
            for (Map.Entry<String, JsonValue> orgEntry : value.getJsonObject("organizations").entrySet()) {

                OrganizationConfig organizationConfig = new OrganizationConfig(orgEntry.getKey(), (JsonObject) orgEntry.getValue());
                orgs.put(organizationConfig.getName(), organizationConfig);
            }

        }

        return orgs;
    }

    Map<String, OrdererConfig> orderers = null;

    Map<String, OrdererConfig> getOrderers() {
        if (orderers == null) {
            orderers = new HashMap<>();
            for (Map.Entry<String, JsonValue> orgEntry : value.getJsonObject("orderers").entrySet()) {

                OrdererConfig ordererConfig = new OrdererConfig(orgEntry.getKey(), (JsonObject) orgEntry.getValue());
                orderers.put(ordererConfig.getName(), ordererConfig);
            }

        }

        return orderers;
    }

    Map<String, ChannelConfig> channels = null;

    Map<String, ChannelConfig> getChannels() {
        if (null == channels) {

            channels = new HashMap<>();
            for (Map.Entry<String, JsonValue> orgEntry : value.getJsonObject("channels").entrySet()) {

                ChannelConfig channelConfig = new ChannelConfig(orgEntry.getKey(), (JsonObject) orgEntry.getValue());
                channels.put(channelConfig.getName(), channelConfig);
            }

        }

        return channels;
    }

    ChannelConfig getChannel(String name) {

        return getChannels().get(name);
    }

    OrdererConfig getOrderer(String name) {
        return getOrderers().get(name);
    }

    static class NetworkConfigUser implements User {
        private final String mspId;
        private final String enrollSecret;
        String name;
        private String affiliation;

        public void setEnrollment(Enrollment enrollment) {
            this.enrollment = enrollment;
        }

        Enrollment enrollment;

        NetworkConfigUser(String name, String mspId, String enrollSecret) {
            this.name = name;
            this.mspId = mspId;
            this.enrollSecret = enrollSecret;

        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getRoles() {
            return null;
        }

        @Override
        public String getAccount() {
            return null;
        }

        @Override
        public String getAffiliation() {
            return affiliation;
        }

        @Override
        public Enrollment getEnrollment() {
            return enrollment;
        }

        @Override
        public String getMspId() {
            return mspId;
        }

        String getEnrollSecret() {
            return enrollSecret;
        }
    }

    class OrganizationConfig {
        private final JsonObject value;
        private final String name;

        OrganizationConfig(String key, JsonObject value) {
            this.value = value;
            this.name = key;
        }

        String getName() {
            return name;
        }

        String getMspid() {
            return value.getString("mspid");
        }

        public OrganizationConfig.CertificateAuthorityConfig getCertificateAuthority(String name) {
            return getCertificateAuthorities().get(name);
        }

        Map<String, OrganizationConfig.CertificateAuthorityConfig> certificateAuthorities;

        public Map<String, OrganizationConfig.CertificateAuthorityConfig> getCertificateAuthorities() {
            if (null == certificateAuthorities) {

                JsonObject cas = NetworkConfig.this.value.getJsonObject("certificateAuthorities");

                certificateAuthorities = new HashMap<>();
                for (Map.Entry<String, JsonValue> ca : cas.entrySet()) {

                    OrganizationConfig.CertificateAuthorityConfig cao = new OrganizationConfig.CertificateAuthorityConfig(ca.getKey(), ca.getValue().asJsonObject(), OrganizationConfig.this.getMspid());
                    certificateAuthorities.put(cao.getName(), cao);

                }

            }
            return certificateAuthorities;

        }

        class CertificateAuthorityConfig extends EndPoint {

            final String mspid;

            private CertificateAuthorityConfig(String name, JsonObject value, String mspid) {
                super(name, value);

                this.mspid = mspid;

            }

            public String getCAName() {
                return value.getString("caName");
            }

            NetworkConfigUser getRegistrar(String name) {
                return getRegistrars().get(name);
            }

            public Map<String, NetworkConfigUser> getRegistrars() {

                Map<String, NetworkConfigUser> ret = new HashMap<>();

                JsonArray registrars = value.getJsonArray("registrar");

                assert registrars != null : "Expected registars to not be null.";

                for (Iterator<JsonValue> it = registrars.iterator(); it.hasNext();
                        ) {
                    JsonObject x = (JsonObject) it.next();
                    Registrar registrar = new Registrar(x);

                    NetworkConfigUser networkConfigUser = new NetworkConfigUser(registrar.getEnrollId(),
                            OrganizationConfig.this.getMspid(), registrar.getEnrollSecret());
                    networkConfigUser.affiliation = registrar.getAffiliation();

                    ret.put(registrar.getEnrollId(), networkConfigUser);

                }

                return ret;
            }

        }

        Map<String, OrganizationConfig.PeerConfig> peers;

        Map<String, OrganizationConfig.PeerConfig> getPeers() {

            if (peers == null) {

                peers = new HashMap<>();

                for (Map.Entry<String, JsonValue> pe : NetworkConfig.this.value.getJsonObject("peers").entrySet()) {

                    OrganizationConfig.PeerConfig peerConfig = new OrganizationConfig.PeerConfig(pe.getKey(), pe.getValue().asJsonObject());
                    peers.put(peerConfig.getName(), peerConfig);

                }
            }

            return peers;

        }

        OrganizationConfig.PeerConfig getPeer(String name) {

            return getPeers().get(name);

        }

        class PeerConfig extends EndPoint {

            PeerConfig(String name, JsonObject value) {
                super(name, value);
            }

            public String getEventURL() {
                return value.getString("eventUrl");
            }

        }

    }

    class Registrar {
        private final JsonObject value;

        Registrar(JsonObject value) {

            this.value = value;
        }

        public String getEnrollId() {
            return value.getString("enrollId");
        }

        public String getAffiliation() {
            return value.getString("affiliation");
        }

        public String getEnrollSecret() {
            return value.getString("enrollSecret");
        }

    }

    class EndPoint {

        final JsonObject value;
        public String name;

        EndPoint(String name, JsonObject value) {
            this.name = name;
            this.value = value;
        }

        public String getURL() {
            return value.getString("url");
        }

        public String getName() {
            return name;
        }

        public String getTLSCerts() {
            return value.getJsonObject("tlsCACerts").getString("pem");

        }

        Properties properties = null;

        public Properties getProperties() {
            if (properties == null) {
                properties = new Properties();
                if (value.containsKey("grpcOptions")) {
                    JsonObject grpcOptions = value.getJsonObject("grpcOptions");
                    if (null != grpcOptions) {
                        JsonNumber jsonNumber = grpcOptions.getJsonNumber("grpc.http2.keepalive_time");
                        if (null != jsonNumber) {
                            properties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {jsonNumber.longValue(), TimeUnit.MINUTES});
                        }
                    }
                }

            }
            return properties;
        }
    }

    class OrdererConfig extends EndPoint {
        OrdererConfig(String name, JsonObject value) {
            super(name, value);
        }

    }

    class ChannelConfig {
        final String name;
        private final JsonObject value;

        public ChannelConfig(String name, JsonObject value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        Map<String, OrdererConfig> orderers = null;

        Map<String, OrdererConfig> getOrderers() {
            if (orderers == null) {

                orderers = new HashMap<>();

                JsonArray orderersNames = value.getJsonArray("orderers");

                assert orderers != null : "Expected registars to not be null.";

                for (Iterator<JsonValue> it = orderersNames.iterator(); it.hasNext();
                        ) {
                    JsonString ordererName = (JsonString) it.next();
                    OrdererConfig ordererConfig = NetworkConfig.this.getOrderer(ordererName.getString());
                    assert ordererConfig != null : format("Orderer %s for channel %s not found", ordererConfig, name);
                    orderers.put(ordererName.getString(), ordererConfig);

                }
            }

            return orderers;
        }

        OrdererConfig getOrderer(String name) {
            return getOrderers().get(name);
        }

        Map<String, OrganizationConfig.PeerConfig> peers = null;

        Map<String, OrganizationConfig.PeerConfig> getPeers() {
            if (peers == null) {

                peers = new HashMap<>();

                for (String peerName : value.getJsonObject("peers").keySet()) {

                    OrganizationConfig.PeerConfig peerConfig =
                            NetworkConfig.this.getOrganization(BMXHyperledgerFabricJSDKIntegrationSample.NETWORK_CONFIG_PEERORG).getPeer(peerName);
                    assert peerConfig != null : format("Peer %s not found for channel %s", peerName, name);
                    peers.put(peerName, peerConfig);
                }
            }

            return peers;
        }

        OrganizationConfig.PeerConfig getPeer(String name) {
            return getPeers().get(name);

        }

    }
}
