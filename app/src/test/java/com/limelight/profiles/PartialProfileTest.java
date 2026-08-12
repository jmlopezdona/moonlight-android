package com.limelight.profiles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.EditProfileActivity;
import com.limelight.R;
import com.limelight.TestLogSuppressor;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * A profile stores only what it changes relative to the global prefs, so keys it leaves
 * out keep coming from the globals instead of freezing at the values they had the day
 * the profile was created.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class PartialProfileTest {
    private static final String RESOLUTION = "list_resolution";
    private static final String BITRATE = "seekbar_bitrate_kbps";

    private Context context;
    private ProfilesManager pm;
    private File profilesDir;

    @BeforeClass
    public static void suppressLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        ProfilesManager.instance = null;
        pm = ProfilesManager.getInstance();
        pm.load(context);

        // PcView does this on startup, so by the time the profile editor can be reached
        // the globals already hold every preference. Without it the diff baseline would be
        // sparse and every default the editor writes would look like a deliberate override.
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

        // A global config that differs from the framework defaults, so a profile that
        // wrongly pinned defaults would be visible.
        globals().edit()
                .putString(RESOLUTION, "1920x1080")
                .putInt(BITRATE, 20000)
                .commit();
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    private SharedPreferences globals() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** Drives the edit screen: applies the given overrides, then saves. */
    private void editAndSave(String profileUuid, Map<String, Object> overrides) {
        Intent intent = new Intent(context, EditProfileActivity.class);
        if (profileUuid != null) {
            intent.putExtra("profileUuid", profileUuid);
        }
        ActivityController<EditProfileActivity> controller =
                Robolectric.buildActivity(EditProfileActivity.class, intent).setup();
        EditProfileActivity activity = controller.get();

        SharedPreferences.Editor editor = activity.getInMemoryPrefs().edit();
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            if (e.getValue() instanceof String) {
                editor.putString(e.getKey(), (String) e.getValue());
            } else if (e.getValue() instanceof Integer) {
                editor.putInt(e.getKey(), (Integer) e.getValue());
            } else if (e.getValue() instanceof Boolean) {
                editor.putBoolean(e.getKey(), (Boolean) e.getValue());
            } else {
                fail("unhandled override type for " + e.getKey());
            }
        }
        editor.apply();

        assertTrue("save menu item should be handled",
                Shadows.shadowOf(activity).clickMenuItem(R.id.action_save));
    }

    private Map<String, Object> override(String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private SettingsProfile onlyProfile() {
        assertEquals(1, pm.getProfiles().size());
        return pm.getProfiles().get(0);
    }

    @Test
    public void newProfile_changingOneSetting_storesOnlyThatKey() {
        editAndSave(null, override(RESOLUTION, "2560x1440"));

        Map<String, Object> options = onlyProfile().getOptions();
        assertEquals("a profile should store only what it changes", 1, options.size());
        assertEquals("2560x1440", options.get(RESOLUTION));
    }

    @Test
    public void newProfile_withNoChanges_storesNothing() {
        editAndSave(null, new HashMap<>());

        assertTrue("an unchanged profile should be empty", onlyProfile().getOptions().isEmpty());
    }

    @Test
    public void editingProfile_settingValueBackToGlobal_dropsTheKey() {
        editAndSave(null, override(BITRATE, 50000));
        SettingsProfile saved = onlyProfile();
        assertEquals(1, saved.getOptions().size());

        // Put the bitrate back to whatever the globals hold
        editAndSave(saved.getUuid().toString(), override(BITRATE, 20000));

        assertTrue("a value equal to the global should not stay pinned",
                onlyProfile().getOptions().isEmpty());
    }

    @Test
    public void reopeningPartialProfile_showsGlobalsNotFrameworkDefaults() {
        editAndSave(null, override(RESOLUTION, "2560x1440"));
        String uuid = onlyProfile().getUuid().toString();

        Intent intent = new Intent(context, EditProfileActivity.class);
        intent.putExtra("profileUuid", uuid);
        EditProfileActivity activity =
                Robolectric.buildActivity(EditProfileActivity.class, intent).setup().get();

        SharedPreferences seen = activity.getInMemoryPrefs();
        assertEquals("the profile's own key wins", "2560x1440", seen.getString(RESOLUTION, null));
        assertEquals("a key the profile doesn't pin must show the global value",
                20000, seen.getInt(BITRATE, -1));
    }

    @Test
    public void reopeningPartialProfile_andSavingUntouched_doesNotInflateIt() {
        editAndSave(null, override(RESOLUTION, "2560x1440"));
        String uuid = onlyProfile().getUuid().toString();

        editAndSave(uuid, new HashMap<>());

        Map<String, Object> options = onlyProfile().getOptions();
        assertEquals("reopening and saving must not pin extra keys", 1, options.size());
        assertEquals("2560x1440", options.get(RESOLUTION));
    }

    @Test
    public void partialProfile_letsLaterGlobalChangesThrough() {
        Map<String, Object> options = new HashMap<>();
        options.put(RESOLUTION, "2560x1440");
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "seat", 0L, 0L, options);
        pm.add(p);
        pm.setActive(p.getUuid());

        // A global changed after the profile was created
        globals().edit().putInt(BITRATE, 35000).commit();

        SharedPreferences overlay = pm.getOverlayingSharedPreferences(context);
        assertEquals("the profile keeps its own key", "2560x1440", overlay.getString(RESOLUTION, null));
        assertEquals("a key the profile omits must follow the globals",
                35000, overlay.getInt(BITRATE, -1));
    }

    @Test
    public void legacyFullProfile_stillAppliesEveryStoredValue() {
        // Profiles saved before this change hold every preference
        Map<String, Object> full = new HashMap<>(globals().getAll());
        full.put(RESOLUTION, "3840x2160");
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "legacy", 0L, 0L, full);
        pm.add(p);
        pm.setActive(p.getUuid());

        globals().edit().putInt(BITRATE, 35000).commit();

        SharedPreferences overlay = pm.getOverlayingSharedPreferences(context);
        assertEquals("3840x2160", overlay.getString(RESOLUTION, null));
        assertEquals("a full profile still shadows the globals it stored",
                20000, overlay.getInt(BITRATE, -1));
    }

    @Test
    public void legacyFullProfile_shrinksWhenReopenedAndSaved() {
        // Numbers come back from Gson as Double, so the reduction only works if values
        // are compared numerically rather than by equals().
        Map<String, Object> full = new HashMap<>();
        full.put(RESOLUTION, "2560x1440");
        full.put(BITRATE, 20000.0d); // same as the global, but a Double like Gson yields
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "legacy", 0L, 0L, full);
        pm.add(p);

        editAndSave(p.getUuid().toString(), new HashMap<>());

        Map<String, Object> options = onlyProfile().getOptions();
        assertEquals("keys equal to the globals should drop, whatever their number type",
                1, options.size());
        assertEquals("2560x1440", options.get(RESOLUTION));
    }

    @Test
    public void partialProfile_readsThroughPreferenceConfiguration() {
        Map<String, Object> options = new HashMap<>();
        options.put(RESOLUTION, "2560x1440");
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "seat", 0L, 0L, options);
        pm.add(p);
        pm.setActive(p.getUuid());

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        assertEquals(2560, config.width);
        assertEquals(1440, config.height);
        assertEquals("the bitrate must come from the globals", 20000, config.bitrate);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
