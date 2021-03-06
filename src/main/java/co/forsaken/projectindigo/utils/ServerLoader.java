package co.forsaken.projectindigo.utils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import co.forsaken.api.json.JsonWebCall;
import co.forsaken.projectindigo.data.Mod;
import co.forsaken.projectindigo.data.Mod.ModType;
import co.forsaken.projectindigo.data.Server;
import co.forsaken.projectindigo.utils.mojangtokens.AssetIndex;
import co.forsaken.projectindigo.utils.mojangtokens.AssetObject;
import co.forsaken.projectindigo.utils.mojangtokens.EnumTypeAdapterFactory;
import co.forsaken.projectindigo.utils.mojangtokens.FileTypeAdapter;
import co.forsaken.projectindigo.utils.mojangtokens.Library;
import co.forsaken.projectindigo.utils.mojangtokens.MojangVersion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.bind.DateTypeAdapter;

public abstract class ServerLoader {

  public static final String     MOJANG_DOWNLOAD_BASE  = "http://s3.amazonaws.com/Minecraft.Download/";
  public static final String     MOJANG_RESOURCES_BASE = "http://resources.download.minecraft.net/";
  public static final String     MOJANG_LIBS_BASE      = "https://libraries.minecraft.net/";
  protected static final Pattern REGEX_URL             = Pattern.compile("href=[\'\"]?([^\'\" >]+)");
  protected static final Pattern REGEX_MODNAME         = Pattern.compile("<a[^>]*>(.*?)</a>");
  @Getter protected boolean      wholeDownload;
  private static boolean         loadedResources       = false;

  public ServerLoader(Server _server, boolean _wholeDownload) {
    if (load(_server)) {
      _server.online = true;
    } else {
      _server.online = false;
    }
    wholeDownload = _wholeDownload;
  }

  public abstract boolean load(Server server);

  public abstract String getDownloadUrl(Server server);

  public abstract String getDownloadUrl(Server server, String modname);

  public Gson getGson() {
    return new GsonBuilder().setPrettyPrinting().registerTypeAdapterFactory(new EnumTypeAdapterFactory()).registerTypeAdapter(Date.class, new DateTypeAdapter()).registerTypeAdapter(File.class, new FileTypeAdapter()).create();
  }

  protected int parseVersion(String version) {
    Pattern pat = Pattern.compile("\\.");
    Matcher match = pat.matcher(version);
    if (match.find()) {
      version = match.replaceAll("");
    }
    return Integer.parseInt(version);
  }

  private List<Mod> getResources(final String version, final Server server) {
    List<Mod> mods = new ArrayList<Mod>();
    File objectsFolder = new File(DirectoryLocations.BACKEND_ASSET_DIR.format("objects/"));
    File indexesFolder = new File(DirectoryLocations.BACKEND_ASSET_DIR.format("indexes/"));
    File virtualFolder = new File(DirectoryLocations.BACKEND_ASSET_DIR.format("virtual/"));
    File virtualRoot = new File(virtualFolder, version);
    File indexFile = new File(indexesFolder, version + ".json");
    objectsFolder.mkdirs();
    indexesFolder.mkdirs();
    virtualFolder.mkdirs();
    try {
      org.apache.commons.io.FileUtils.copyURLToFile(new URL(MOJANG_DOWNLOAD_BASE + "indexes/" + version + ".json"), indexFile);
      AssetIndex index = (AssetIndex) (new Gson()).fromJson(new FileReader(indexFile), AssetIndex.class);
      if (index.isVirtual()) {
        virtualRoot.mkdirs();
      }

      for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
        AssetObject object = entry.getValue();
        String filename = object.getHash().substring(0, 2) + "/" + object.getHash();
        String folderName = object.getHash().substring(0, 2);
        File folder = new File(objectsFolder, folderName + "/");
        if (!folder.exists()) folder.mkdirs();
        File file = new File(objectsFolder, filename);
        File virtualFile = new File(virtualRoot, entry.getKey());
        if (object.needToDownload(file) && !file.exists()) {
          mods.add(new Mod(object.getHash(), new ArrayList<String>(), MOJANG_RESOURCES_BASE + filename, folder.getAbsolutePath(), ModType.assets));
        } else {
          if (index.isVirtual()) {
            virtualFile.mkdirs();
            FileUtils.copyFile(file, virtualFile, true);
          }
        }
      }
    } catch (JsonSyntaxException e) {
      e.printStackTrace();
    } catch (JsonIOException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return mods;
  }

  public List<Mod> getMojangLibraries(Server server, String version) {
    List<Mod> mods = new ArrayList<Mod>();
    String s = "";
    try {
      s = new JsonWebCall(MOJANG_DOWNLOAD_BASE + "versions/" + version + "/" + version + ".json").executeGet();
    } catch (Exception e) {
      e.printStackTrace();
    }
    MojangVersion token = getGson().fromJson(s, MojangVersion.class);
    for (Library library : token.getLibraries()) {
      if (library.shouldInstall()) {
        if (library.shouldExtract()) {
          mods.add(new Mod(library.getURL(), new ArrayList<String>(), library.getURL(), "", ModType.natives));
        } else {
          mods.add(new Mod(library.getURL(), new ArrayList<String>(), library.getURL(), "", ModType.library));
        }
      }
    }
    mods.addAll(getResources(version, server));
    server.setLaunchArgs(token.getMinecraftArguments());
    return mods;
  }
}
