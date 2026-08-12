package com.limelight.profiles;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Per-PC profile bindings: the three states, how they're applied on launch, and the
 * cleanup that keeps them from pointing at things that no longer exist.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class PcBindingTest {
    private static final String PC_PADRE = "pc-padre-uuid";
    private static final String PC_HIJO = "pc-hijo-uuid";

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
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    private SettingsProfile addProfile(String name) {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), name, 0L, 0L, null);
        pm.add(p);
        return p;
    }

    private File profilesFile() {
        return new File(profilesDir, "profiles.json");
    }

    // ---- the three states -------------------------------------------------------------

    @Test
    public void pcWithoutBinding_isUnassigned() {
        assertNull(pm.getPcBinding(PC_PADRE));
    }

    @Test
    public void bindPc_toProfile_persistsAcrossReload() {
        SettingsProfile p = addProfile("seat 1440p");
        pm.bindPc(PC_PADRE, p.getUuid());

        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertEquals(p.getUuid().toString(), fresh.getPcBinding(PC_PADRE));
    }

    @Test
    public void bindPc_toNull_meansGlobalSettings() {
        pm.bindPc(PC_PADRE, null);
        assertEquals(ProfilesManager.BINDING_NONE, pm.getPcBinding(PC_PADRE));
    }

    @Test
    public void unbindPc_returnsToUnassigned() {
        SettingsProfile p = addProfile("seat");
        pm.bindPc(PC_PADRE, p.getUuid());
        pm.unbindPc(PC_PADRE);

        assertNull(pm.getPcBinding(PC_PADRE));
    }

    // ---- applyProfileForPc: the six rules ---------------------------------------------

    @Test
    public void apply_withEmptyUuid_changesNothing() {
        SettingsProfile p = addProfile("active");
        pm.setActive(p.getUuid());

        assertFalse(pm.applyProfileForPc(null));
        assertFalse(pm.applyProfileForPc(""));
        assertEquals(p.getUuid(), pm.getActive().getUuid());
    }

    @Test
    public void apply_whenPcIsUnassigned_leavesActiveProfileAlone() {
        SettingsProfile p = addProfile("manual choice");
        pm.setActive(p.getUuid());

        assertFalse(pm.applyProfileForPc(PC_PADRE));
        assertEquals(p.getUuid(), pm.getActive().getUuid());
    }

    @Test
    public void apply_withBindingNone_clearsTheActiveProfile() {
        SettingsProfile p = addProfile("console 4K");
        pm.setActive(p.getUuid());
        pm.bindPc(PC_HIJO, null);

        assertTrue(pm.applyProfileForPc(PC_HIJO));
        assertNull(pm.getActive());
    }

    @Test
    public void apply_withBoundProfile_activatesIt() {
        SettingsProfile seat = addProfile("seat 1440p");
        SettingsProfile console = addProfile("console 4K");
        pm.setActive(console.getUuid());
        pm.bindPc(PC_PADRE, seat.getUuid());

        assertTrue(pm.applyProfileForPc(PC_PADRE));
        assertEquals(seat.getUuid(), pm.getActive().getUuid());
    }

    @Test
    public void apply_withStaleBinding_treatsItAsUnassignedAndClearsIt() {
        SettingsProfile active = addProfile("active");
        pm.setActive(active.getUuid());
        // A binding to a profile that isn't in the store
        pm.bindPc(PC_HIJO, UUID.randomUUID());

        assertFalse(pm.applyProfileForPc(PC_HIJO));
        assertEquals("the active profile must be untouched",
                active.getUuid(), pm.getActive().getUuid());
        assertNull("the stale binding must be dropped", pm.getPcBinding(PC_HIJO));
    }

    @Test
    public void apply_whenAlreadyActive_doesNotRewriteTheStore() throws IOException {
        SettingsProfile seat = addProfile("seat 1440p");
        pm.bindPc(PC_PADRE, seat.getUuid());
        pm.setActive(seat.getUuid());

        String marker = stampFile();
        assertFalse("nothing changed, so no re-activation", pm.applyProfileForPc(PC_PADRE));
        assertTrue("profiles.json must not be rewritten on every launch", markerSurvived(marker));
    }

    @Test
    public void apply_whenAlreadyUnset_doesNotRewriteTheStore() throws IOException {
        pm.bindPc(PC_HIJO, null);
        assertNull(pm.getActive());

        String marker = stampFile();
        assertFalse(pm.applyProfileForPc(PC_HIJO));
        assertTrue(markerSurvived(marker));
    }

    @Test
    public void apply_isRepeatableAndOnlyReportsRealChanges() {
        SettingsProfile seat = addProfile("seat");
        pm.bindPc(PC_PADRE, seat.getUuid());

        assertTrue("first launch activates it", pm.applyProfileForPc(PC_PADRE));
        assertFalse("second launch has nothing to do", pm.applyProfileForPc(PC_PADRE));
        assertEquals(seat.getUuid(), pm.getActive().getUuid());
    }

    // ---- cleanup ----------------------------------------------------------------------

    @Test
    public void deletingProfile_unbindsEveryPcThatUsedIt() {
        SettingsProfile shared = addProfile("seat 1080p");
        SettingsProfile other = addProfile("console 4K");
        pm.bindPc(PC_PADRE, shared.getUuid());
        pm.bindPc(PC_HIJO, shared.getUuid());
        pm.bindPc("pc-consola-uuid", other.getUuid());

        pm.delete(shared.getUuid());

        assertNull(pm.getPcBinding(PC_PADRE));
        assertNull(pm.getPcBinding(PC_HIJO));
        assertEquals("an unrelated binding must survive",
                other.getUuid().toString(), pm.getPcBinding("pc-consola-uuid"));
    }

    @Test
    public void deletingProfile_leavesBoundPcsUsableAfterReload() {
        SettingsProfile p = addProfile("seat");
        pm.bindPc(PC_HIJO, p.getUuid());
        pm.delete(p.getUuid());

        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertNull(fresh.getPcBinding(PC_HIJO));
        assertFalse(fresh.applyProfileForPc(PC_HIJO));
    }

    // ---- compatibility ---------------------------------------------------------------

    @Test
    public void loadsFileWrittenBeforeBindingsExisted() throws IOException {
        UUID id = UUID.randomUUID();
        assertTrue(profilesDir.exists() || profilesDir.mkdirs());
        try (Writer w = new FileWriter(profilesFile())) {
            w.write("{\"profiles\":[{\"uuid\":\"" + id + "\",\"name\":\"legacy\","
                    + "\"createdUtc\":0,\"modifiedUtc\":0,\"options\":{}}],"
                    + "\"activeProfileId\":\"" + id + "\"}");
        }

        ProfilesManager fresh = ProfilesManager.getInstance();
        assertTrue("a file without pcBindings must still load", fresh.load(context));
        assertEquals(1, fresh.getProfiles().size());
        assertNotNull(fresh.getActive());
        assertNull("every PC starts unassigned", fresh.getPcBinding(PC_PADRE));
        assertFalse(fresh.applyProfileForPc(PC_PADRE));
    }

    /** Appends a marker to profiles.json; if save() runs, the marker is overwritten. */
    private String stampFile() throws IOException {
        File f = profilesFile();
        assertTrue("expected a stored file to stamp", f.exists());
        String marker = "\n// marker-" + UUID.randomUUID();
        Files.write(f.toPath(), marker.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        return marker;
    }

    private boolean markerSurvived(String marker) throws IOException {
        return new String(Files.readAllBytes(profilesFile().toPath()), StandardCharsets.UTF_8)
                .contains(marker);
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
