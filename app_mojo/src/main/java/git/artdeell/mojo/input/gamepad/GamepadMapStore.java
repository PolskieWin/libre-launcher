package git.artdeell.mojo.input.gamepad;

import android.util.Log;

import com.google.gson.JsonParseException;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.IOException;

public class GamepadMapStore {
    private static File getStoreFile() {
        return new File(Tools.DIR_DATA != null ? Tools.DIR_DATA : "", "gamepad_map.json");
    }
    private static GamepadMapStore sMapStore;
    private GamepadMap mInMenuMap;
    private GamepadMap mInGameMap;
    private static GamepadMapStore createDefault() {
        GamepadMapStore mapStore = new GamepadMapStore();
        mapStore.mInGameMap = GamepadMap.getDefaultGameMap();
        mapStore.mInMenuMap = GamepadMap.getDefaultMenuMap();
        return mapStore;
    }

    private static void loadIfNecessary() {
        if(sMapStore == null) return;
        load();
    }

    public static void load() {
        GamepadMapStore mapStore = null;
        File storeFile = getStoreFile();
        if(storeFile.exists() && storeFile.canRead()) {
            try {
                String storeFileContent = Tools.read(storeFile);
                mapStore = Tools.GLOBAL_GSON.fromJson(storeFileContent, GamepadMapStore.class);
            } catch (JsonParseException | IOException e) {
                Log.w("GamepadMapStore", "Map store failed to load!", e);
            }
        }
        if(mapStore == null) mapStore = createDefault();
        sMapStore = mapStore;
    }

    public static void save() throws IOException {
        if(sMapStore == null) throw new RuntimeException("Must load map store first!");
        File storeFile = getStoreFile();
        FileUtils.ensureParentDirectory(storeFile);
        String jsonData = Tools.GLOBAL_GSON.toJson(sMapStore);
        Tools.write(storeFile, jsonData);
    }

    public static GamepadMap getGameMap() {
        loadIfNecessary();
        return sMapStore.mInGameMap;
    }

    public static GamepadMap getMenuMap() {
        loadIfNecessary();
        return sMapStore.mInMenuMap;
    }
}
