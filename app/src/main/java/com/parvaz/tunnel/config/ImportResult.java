package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import java.util.ArrayList;

/** Structured diagnostics contain only fixed codes and positions, NEVER input/credentials. */
public final class ImportResult {
    public final ArrayList<Profile> profiles = new ArrayList<>();
    public final ArrayList<String> issues = new ArrayList<>();
    public int rejected;
    public int warnings;
    public boolean fatal;

    public void reject(String code, int position) {
        rejected++;
        issue(code, position);
    }
    public void warn(String code, int position) {
        warnings++;
        issue(code, position);
    }
    public void fail(String code) { fatal = true; reject(code, 0); }
    private void issue(String code, int position) {
        if (issues.size() < 100) issues.add(code + " #" + position);
    }
    public void add(Profile profile) {
        if (profiles.size() >= LinkParser.MAX_PROFILES) throw new IllegalArgumentException("Too many subscription profiles");
        profiles.add(profile);
    }
    public void merge(ImportResult other) {
        for (Profile p : other.profiles) add(p);
        rejected += other.rejected;
        warnings += other.warnings;
        fatal |= other.fatal;
        for (String issue : other.issues) if (issues.size() < 100) issues.add(issue);
    }
    /** Automatic destructive replacement must NEVER use a partial/failed parse. */
    public boolean safeToReplace() { return !fatal && rejected == 0 && !profiles.isEmpty(); }
}
