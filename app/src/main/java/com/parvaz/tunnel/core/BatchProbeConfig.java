package com.parvaz.tunnel.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes ONE engine configuration that can measure many profiles at the same time.
 *
 * <p>Why: the previous design started a whole isolated core per profile, so a batch of
 * N servers paid N core start/stop cycles behind a three permit admission gate. Here each
 * accepted profile contributes its own already-built {@code proxy} outbound, re-tagged, plus
 * a private loopback HTTP inbound, and one routing rule pinning that inbound to that outbound
 * and to nothing else.
 *
 * <p>Safety rules that must not be relaxed:
 * <ul>
 *   <li>No {@code direct}/{@code freedom} catch-all outbound is ever copied in, so a probe
 *       cannot silently measure the untunnelled path. The only non-proxy outbound allowed is
 *       the shared TLS {@code fragment} dialer, and only when an accepted outbound asks for it.</li>
 *   <li>Every inbound has exactly one rule, and that rule names exactly one outbound.</li>
 *   <li>A profile whose config does not contain exactly one remote outbound tagged
 *       {@code proxy} is rejected here and measured by the per-profile path instead.</li>
 * </ul>
 */
public final class BatchProbeConfig {

    /** Remote protocols that prove a route; mirrors {@link com.parvaz.tunnel.config.ReadinessConfig}. */
    private static final List<String> REMOTE =
            Arrays.asList("vmess", "vless", "trojan", "shadowsocks", "socks", "http", "wireguard");

    private static final String OUT_PREFIX = "parvaz-batch-out-";
    private static final String IN_PREFIX = "parvaz-batch-in-";

    private BatchProbeConfig() {
    }

    /** One candidate: the profile id, its individually built config and its loopback port. */
    public static final class Member {
        public final String profileId;
        public final String config;
        public final int port;

        public Member(String profileId, String config, int port) {
            this.profileId = profileId;
            this.config = config;
            this.port = port;
        }
    }

    /** Result of composition: the shared config plus who is in it and who was turned away. */
    public static final class Plan {
        public final String config;
        /** Accepted profile id -> loopback port it must be measured on. */
        public final Map<String, Integer> ports;
        /** Profiles that cannot share a core and need the per-profile fallback. */
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

    public static Plan compose(List<Member> members) throws JSONException {
        JSONArray inbounds = new JSONArray();
        JSONArray proxies = new JSONArray();
        JSONArray rules = new JSONArray();
        Map<String, Integer> ports = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        Set<Integer> usedPorts = new HashSet<>();
        JSONObject fragment = null;

        if (members != null) {
            for (Member member : members) {
                if (member == null || member.profileId == null || member.profileId.isEmpty()) {
                    continue;
                }
                if (ports.containsKey(member.profileId) || rejected.contains(member.profileId)) {
                    continue; // A duplicate row is measured once, never twice in one core.
                }
                if (member.port < 1 || member.port > 65535 || !usedPorts.add(member.port)) {
                    rejected.add(member.profileId);
                    continue;
                }
                JSONObject proxy = selectedOutbound(member.config);
                if (proxy == null) {
                    rejected.add(member.profileId);
                    continue;
                }
                String dialer = dialerProxy(proxy);
                JSONObject needed = null;
                if (!dialer.isEmpty()) {
                    // Only the fragment dialer is understood here; anything else (a chain)
                    // belongs to the per-profile path where the whole config is kept intact.
                    if (!"fragment".equals(dialer)) {
                        rejected.add(member.profileId);
                        continue;
                    }
                    needed = outboundByTag(member.config, "fragment");
                    if (needed == null) {
                        rejected.add(member.profileId);
                        continue;
                    }
                }

                int index = ports.size();
                String outTag = OUT_PREFIX + index;
                String inTag = IN_PREFIX + index;
                proxy.put("tag", outTag);
                proxies.put(proxy);
                if (needed != null && fragment == null) {
                    fragment = needed;
                    fragment.put("tag", "fragment");
                }
                inbounds.put(new JSONObject()
                        .put("tag", inTag)
                        .put("listen", "127.0.0.1")
                        .put("port", member.port)
                        .put("protocol", "http")
                        .put("settings", new JSONObject()));
                rules.put(new JSONObject()
                        .put("type", "field")
                        .put("inboundTag", new JSONArray().put(inTag))
                        .put("outboundTag", outTag));
                ports.put(member.profileId, member.port);
            }
        }

        if (ports.isEmpty()) {
            return new Plan("", ports, rejected);
        }

        // The fragment dialer goes last: an unmatched connection would use the FIRST
        // outbound, and that must never be a dialer or anything direct.
        if (fragment != null) {
            proxies.put(fragment);
        }

        JSONObject root = new JSONObject();
        root.put("log", new JSONObject().put("loglevel", "none"));
        root.put("inbounds", inbounds);
        root.put("outbounds", proxies);
        root.put("routing", new JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules));
        return new Plan(root.toString(), ports, rejected);
    }

    /** Deep copy of the one remote outbound tagged {@code proxy}, or null when ambiguous. */
    private static JSONObject selectedOutbound(String config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(config);
            JSONArray outbounds = root.optJSONArray("outbounds");
            if (outbounds == null) {
                return null;
            }
            JSONObject selected = null;
            Set<String> tags = new HashSet<>();
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject out = outbounds.optJSONObject(i);
                if (out == null) {
                    return null;
                }
                String tag = out.optString("tag");
                if (!tag.isEmpty() && !tags.add(tag)) {
                    return null;
                }
                if ("chain".equals(tag)) {
                    return null; // A chained profile keeps its own core.
                }
                if (!"proxy".equals(tag)) {
                    continue;
                }
                if (selected != null || !REMOTE.contains(out.optString("protocol"))) {
                    return null;
                }
                selected = out;
            }
            return selected == null ? null : new JSONObject(selected.toString());
        } catch (JSONException invalid) {
            return null;
        }
    }

    private static JSONObject outboundByTag(String config, String tag) {
        try {
            JSONArray outbounds = new JSONObject(config).optJSONArray("outbounds");
            if (outbounds == null) {
                return null;
            }
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject out = outbounds.optJSONObject(i);
                if (out != null && tag.equals(out.optString("tag"))) {
                    return new JSONObject(out.toString());
                }
            }
            return null;
        } catch (JSONException invalid) {
            return null;
        }
    }

    private static String dialerProxy(JSONObject outbound) {
        JSONObject stream = outbound.optJSONObject("streamSettings");
        JSONObject sockopt = stream == null ? null : stream.optJSONObject("sockopt");
        return sockopt == null ? "" : sockopt.optString("dialerProxy", "");
    }
}
