package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProfilesManager {
    private static final String PROFILES_DIR = "profiles";
    private static final String PROFILES_FILE = "profiles.json";

    static ProfilesManager instance;

    /**
     * Value meaning "this PC deliberately uses the global settings". A PC with no entry at
     * all is a different state: it doesn't touch the active profile.
     */
    public static final String BINDING_NONE = "";

    private final Map<UUID, SettingsProfile> profiles = new LinkedHashMap<>();
    private final Map<String, String> pcBindings = new LinkedHashMap<>();
    private UUID activeProfileId;
    private final List<ProfileChangeListener> listeners = new ArrayList<>();
    private Context appContext; // Application context for auto-save

    private ProfilesManager() {}

    public static synchronized ProfilesManager getInstance() {
        if (instance == null) {
            instance = new ProfilesManager();
        }
        return instance;
    }

    public boolean load(Context context) {
        LimeLog.info("ArtemisProfile: Loading profile...");
        if (context == null) {
            return false;
        }

        try {
            this.appContext = context.getApplicationContext();
        } catch (Exception e) {
            // If getApplicationContext() fails (e.g., during app startup), use the context directly
            this.appContext = context;
        }

        // Additional safety check
        if (this.appContext == null) {
            return false;
        }

        try {
            File dir = new File(this.appContext.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            if (!file.exists()) {
                // We don't want to warn user about profile not exist
                return true;
            }
            try (Reader reader = new FileReader(file)) {
                Gson gson = new Gson();
                Type type = new TypeToken<ProfilesData>(){}.getType();
                ProfilesData data = gson.fromJson(reader, type);
                if (data != null && data.profiles != null) {
                    profiles.clear();
                    for (SettingsProfile p : data.profiles) {
                        profiles.put(p.getUuid(), p);
                    }
                    activeProfileId = data.activeProfileId;

                    // Absent in files written before per-PC bindings existed
                    pcBindings.clear();
                    if (data.pcBindings != null) {
                        pcBindings.putAll(data.pcBindings);
                    }
                }
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to load profiles from file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to load profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean save(Context context) {
        if (context == null) {
            return false;
        }

        try {
            File dir = new File(context.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            try (Writer writer = new FileWriter(file)) {
                Gson gson = new Gson();
                ProfilesData data = new ProfilesData();
                data.profiles = new ArrayList<>(profiles.values());
                data.activeProfileId = activeProfileId;
                data.pcBindings = new LinkedHashMap<>(pcBindings);
                gson.toJson(data, writer);
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to save profiles to file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to save profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public List<SettingsProfile> getProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public void add(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void update(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void delete(UUID uuid) {
        profiles.remove(uuid);
        if (uuid.equals(activeProfileId)) {
            activeProfileId = null;
        }
        // Drop any PC bound to the profile we just removed, so it goes back to unassigned
        // instead of pointing at something that no longer exists
        pcBindings.values().removeAll(java.util.Collections.singleton(uuid.toString()));
        notifyListeners();
        saveIfPossible();
    }

    public void setActive(UUID uuid) {
        activeProfileId = uuid;
        notifyListeners();
        saveIfPossible();
    }

    public SettingsProfile getActive() {
        return activeProfileId == null ? null : profiles.get(activeProfileId);
    }

    @NonNull
    public String getActiveName() {
        SettingsProfile active = getActive();
        return active == null ? "" : active.getName();
    }

    /**
     * Returns the raw binding for a PC, or null when the PC has no assignment at all.
     * A {@link #BINDING_NONE} value means the PC deliberately uses the global settings.
     */
    public String getPcBinding(String pcUuid) {
        if (pcUuid == null || pcUuid.isEmpty()) {
            return null;
        }
        return pcBindings.get(pcUuid);
    }

    /** Binds a PC to a profile. A null profileUuid means "use the global settings". */
    public void bindPc(String pcUuid, UUID profileUuid) {
        if (pcUuid == null || pcUuid.isEmpty()) {
            return;
        }
        pcBindings.put(pcUuid, profileUuid == null ? BINDING_NONE : profileUuid.toString());
        saveIfPossible();
    }

    /** Removes a PC's assignment, so using it no longer touches the active profile. */
    public void unbindPc(String pcUuid) {
        if (pcUuid == null || pcUuid.isEmpty()) {
            return;
        }
        if (pcBindings.remove(pcUuid) != null) {
            saveIfPossible();
        }
    }

    /**
     * Activates the profile bound to this PC, if any.
     *
     * Runs on every game launch, so it must stay cheap: when the wanted profile is already
     * active it returns without writing profiles.json or notifying listeners. Never throws
     * and never blocks a launch - on any problem it leaves the active profile alone.
     *
     * @return true when the active profile changed, meaning cached preferences must be re-read
     */
    public boolean applyProfileForPc(String pcUuid) {
        try {
            if (pcUuid == null || pcUuid.isEmpty()) {
                return false;
            }

            String binding = pcBindings.get(pcUuid);
            if (binding == null) {
                // Unassigned: leave whatever profile is active alone
                return false;
            }

            UUID wanted = null;
            if (!BINDING_NONE.equals(binding)) {
                try {
                    wanted = UUID.fromString(binding);
                } catch (IllegalArgumentException e) {
                    LimeLog.warning("ArtemisProfile: Unparseable binding for PC " + pcUuid);
                    unbindPc(pcUuid);
                    return false;
                }
                if (!profiles.containsKey(wanted)) {
                    // The profile was deleted behind our back; treat it as unassigned
                    LimeLog.info("ArtemisProfile: Dropping stale binding for PC " + pcUuid);
                    unbindPc(pcUuid);
                    return false;
                }
            }

            if (wanted == null ? activeProfileId == null : wanted.equals(activeProfileId)) {
                return false;
            }

            setActive(wanted);
            return true;
        } catch (Exception e) {
            // Applying a binding must never stop a game from launching
            LimeLog.warning("ArtemisProfile: Failed to apply profile for PC:" + e);
            return false;
        }
    }

    public void addListener(ProfileChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProfileChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ProfileChangeListener listener : listeners) {
            listener.onProfilesChanged();
        }
    }

    private static class ProfilesData {
        List<SettingsProfile> profiles;
        UUID activeProfileId;
        Map<String, String> pcBindings;
    }

    public interface ProfileChangeListener {
        void onProfilesChanged();
    }

    /**
     * Returns a SharedPreferences that overlays the active profile's options on top of the real prefs.
     */
    public SharedPreferences getOverlayingSharedPreferences(Context context) {
        SharedPreferences base = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SettingsProfile active = getActive();
        if (active == null || active.getOptions() == null) {
            return base;
        }
        return new OverlaySharedPreferences(base, active.getOptions());
    }

    /**
     * Wraps a SharedPreferences to override and shadow values from a profile's options map.
     */
    private static class OverlaySharedPreferences implements SharedPreferences {
        private final SharedPreferences base;
        private final Map<String, Object> patch;
        OverlaySharedPreferences(SharedPreferences base, Map<String, Object> patch) {
            this.base = base;
            this.patch = patch;
        }
        @Override public Map<String, ?> getAll() {
            Map<String, Object> combined = new LinkedHashMap<>(base.getAll());
            combined.putAll(patch);
            return combined;
        }
        @Override public String getString(String key, String defValue) {
            if (patch.containsKey(key)) return (String) patch.get(key);
            return base.getString(key, defValue);
        }
        @Override public int getInt(String key, int defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).intValue();
            return base.getInt(key, defValue);
        }
        @Override public long getLong(String key, long defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).longValue();
            return base.getLong(key, defValue);
        }
        @Override public float getFloat(String key, float defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).floatValue();
            return base.getFloat(key, defValue);
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            if (patch.containsKey(key)) return (Boolean) patch.get(key);
            return base.getBoolean(key, defValue);
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            if (patch.containsKey(key)) return (Set<String>) patch.get(key);
            return base.getStringSet(key, defValues);
        }
        @Override public boolean contains(String key) {
            return patch.containsKey(key) || base.contains(key);
        }
        @Override public Editor edit() { return base.edit(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.registerOnSharedPreferenceChangeListener(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private boolean saveIfPossible() {
        if (appContext != null) {
            return save(appContext);
        }
        return false;
    }
}