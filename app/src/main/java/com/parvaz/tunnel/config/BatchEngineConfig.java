package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes ONE sing-box configuration that measures many external-engine profiles at once.
 *
 * <p>This is the external-core twin of {@link com.parvaz.tunnel.core.BatchProbeConfig}. Until
 * now Hysteria2, TUIC, Hysteria v1, AnyTLS and Snell rows were the slow part of every "ping
 * all": each one started a whole sing-box child process, waited for it to come up, measured a
 * single server and tore it down, all behind a three permit gate. Here every accepted profile
 * contributes its own outbound plus a private loopback HTTP inbound, and one route rule pins
 * that inbound to that outbound and to nothing else.
 *
 * <p>Rules that must not be relaxed:
 * <ul>
 *   <li>No direct outbound exists, so a probe can never silently measure the untunnelled path.
 *       The only non-profile outbound is a block sink used as the route default, which fails
 *       closed instead of leaking an unmatched connection into the first profile's tunnel.</li>
 *   <li>Every inbound has exactly one rule and that rule names exactly one outbound.</li>
 *   <li>Full Clash and full sing-box profiles are never merged: their own routing is the
 *       thing under test, so they keep the per-profile path.</li>
 * </ul>
 */
public final class BatchEngineConfig {

    private static final String OUT_PREFIX = "parvaz-engine-out-";
    private static final String IN_PREFIX = "parvaz-engine-in-";
    /** Tag of the fail-closed sink used for anything that matches no rule. */
    public static final String SINK = "parvaz-engine-sink";

    private BatchEngineConfig() {
    }

    /** One candidate: the profile and the loopback port it must be measured on. */
    public static final class Member {
        public final Profile profile;
        public final int port;

        public Member(Profile profile, int port) {
            this.profile = profile;
            this.port = port;
        }
    }

    /** The composed configuration plus who is in it and who was turned away. */
    public static final class Plan {
        public final String config;
        /** Accepted profile id -> loopback port it must be measured on. */
        public final Map<String, Integer> ports;
        /** Profile ids that cannot share an engine and need the per-profile path. */
        public final List<String> rejected;

        Plan(String config, Map<String, Integer> ports, List<String> rejected) {
            this.config = config;
            this.ports = Collections.unmodifiableMap(ports);
            this.rejected = Collections.unmodifiableList(rejected);
        }

        public boolean isEmpty() {
            return ports.isEmpty();
        }
    }

    /** True when this profile's whole route is one sing-box outbound that can be shared. */
    public static boolean shareable(Profile profile) {
        if (profile == null || profile.protocol == null) {
            return false;
        }
        String protocol = ProtocolNames.canonical(profile.protocol);
        if (FullConfig.isFull(protocol)) {
            return false;
        }
        // Either a protocol only sing-box speaks, or one it can reproduce exactly - the
        // latter matters when the user has put an Xray protocol on sing-box by choice.
        return EngineConfig.external(protocol) || SingBoxOutbound.capable(profile);
    }

    public static Plan compose(List<Member> members) {
        JSONArray inbounds = new JSONArray();
        JSONArray outbounds = new JSONArray();
        JSONArray rules = new JSONArray();
        Map<String, Integer> ports = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        Set<Integer> usedPorts = new HashSet<>();

        if (members != null) {
            for (Member member : members) {
                if (member == null || member.profile == null
                        || member.profile.id == null || member.profile.id.isEmpty()) {
                    continue;
                }
                String id = member.profile.id;
                if (ports.containsKey(id) || rejected.contains(id)) {
                    continue; // A duplicate row is measured once, never twice in one engine.
                }
                if (!shareable(member.profile) || member.port < 1 || member.port > 65535
                        || !usedPorts.add(member.port)) {
                    rejected.add(id);
                    continue;
                }
                int index = ports.size();
                String outTag = OUT_PREFIX + index;
                String inTag = IN_PREFIX + index;
                try {
                    outbounds.put(EngineConfig.outbound(member.profile, outTag));
                    inbounds.put(new JSONObject()
                            .put("type", "http")
                            .put("tag", inTag)
                            .put("listen", "127.0.0.1")
                            .put("listen_port", member.port));
                    rules.put(new JSONObject()
                            .put("inbound", new JSONArray().put(inTag))
                            .put("action", "route")
                            .put("outbound", outTag));
                } catch (JSONException | RuntimeException unusable) {
                    rejected.add(id);
                    continue;
                }
                ports.put(id, member.port);
            }
        }

        if (ports.isEmpty()) {
            return new Plan("", ports, rejected);
        }

        try {
            // Fail closed: an unmatched connection is dropped rather than dialled through
            // whichever profile happens to be first in the list.
            outbounds.put(new JSONObject().put("type", "block").put("tag", SINK));
            JSONObject root = new JSONObject();
            root.put("log", new JSONObject().put("disabled", true));
            root.put("inbounds", inbounds);
            root.put("outbounds", outbounds);
            root.put("dns", new JSONObject().put("servers", new JSONArray()
                    .put(new JSONObject().put("type", "https").put("tag", "bootstrap")
                            .put("server", "1.1.1.1").put("path", "/dns-query"))));
            root.put("route", new JSONObject()
                    .put("rules", rules)
                    .put("final", SINK)
                    .put("auto_detect_interface", false)
                    .put("default_domain_resolver", new JSONObject().put("server", "bootstrap")));
            return new Plan(root.toString(), ports, rejected);
        } catch (JSONException invalid) {
            List<String> all = new ArrayList<>(rejected);
            all.addAll(ports.keySet());
            return new Plan("", new LinkedHashMap<String, Integer>(), all);
        }
    }
}
