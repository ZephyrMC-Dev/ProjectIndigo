package co.forsaken.projectindigo.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;

import org.apache.commons.io.FileUtils;

import co.forsaken.projectindigo.IndigoLauncher;
import co.forsaken.projectindigo.data.tokens.ServerToken;
import co.forsaken.projectindigo.gui.LoginPanel;
import co.forsaken.projectindigo.gui.MainPanel;
import co.forsaken.projectindigo.gui.ProgressPanel;
import co.forsaken.projectindigo.gui.SettingsPanel;
import co.forsaken.projectindigo.log.LogManager;
import co.forsaken.projectindigo.mclaunch.MinecraftLauncher;
import co.forsaken.projectindigo.utils.DirectoryLocations;
import co.forsaken.projectindigo.utils.ServerLoader;
import co.forsaken.projectindigo.utils.atlauncher.AtLauncherServerLoader;
import co.forsaken.projectindigo.utils.ftb.FtbServerLoader;
import co.forsaken.projectindigo.utils.ftb.tokens.Artifact;
import co.forsaken.projectindigo.utils.ftb.tokens.PackToken;
import co.forsaken.projectindigo.utils.ftb.tokens.PackToken.Library;
import co.forsaken.projectindigo.utils.technic.TechnicServerLoader;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Data public class Server {
  private ServerToken          token;
  private String               url;
  private String               desc;
  private HashMap<String, Mod> modList;
  private ServerLoader         loader;
  private String               launchArgs;
  private String               jarOrder = "";

  public Server(ServerToken _token) {
    token = _token;
    if (token.modpackType.equalsIgnoreCase("ftb")) {
      loader = new FtbServerLoader(this);
    } else if (token.modpackType.equalsIgnoreCase("atlaunch")) {
      loader = new AtLauncherServerLoader(this);
    } else if (token.modpackType.equalsIgnoreCase("technic")) {
      loader = new TechnicServerLoader(this);
    }
    LogManager.info("Download " + token.friendlyName + (loader != null && loader.isWholeDownload() ? " Whole Modpack " + " from " + getDownloadLocation() : " Mod By Mod "));
    mkdirs();
  }

  public void cleanup() {
    try {
      FileUtils.deleteDirectory(getMinecraftDir());
    } catch (IOException e) {}
    mkdirs();
  }

  private void mkdirs() {
    if (!getMinecraftDir().exists()) getMinecraftDir().mkdirs();
    if (!getModsDir().exists()) getModsDir().mkdir();
    if (!getConfigDir().exists()) getConfigDir().mkdir();
    if (!getLibraryDir().exists()) getLibraryDir().mkdir();
    if (!getResourceDir().exists()) getResourceDir().mkdir();
    if (!getJarModsDir().exists()) getJarModsDir().mkdir();
  }

  public File getLockFile() {
    return new File(getMinecraftDir(), "version.dat");
  }

  public String getDownloadLocation() {
    if (loader == null) { return null; }
    if (loader.isWholeDownload()) {
      return loader.getDownloadUrl(this);
    } else {
      return loader.getDownloadUrl(this, modList.get(modList.keySet().toArray(new String[] {})[0]).getName());
    }
  }

  public boolean needsDownload() {
    if (getLockFile().exists()) {
      try {
        if (FileUtils.readFileToString(getLockFile()).equalsIgnoreCase(getToken().version)) { return false; }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return true;
  }

  public File getMinecraftDir() {
    return new File(String.format(DirectoryLocations.SERVER_DIR_LOCATION, token.friendlyName.replace(" ", "_").replace(".", "_")));
  }

  public File getModsDir() {
    return new File(getMinecraftDir(), "mods");
  }

  public File getConfigDir() {
    return new File(getMinecraftDir(), "config");
  }

  public File getLibraryDir() {
    return new File(getMinecraftDir(), "libraries");
  }

  public File getJarModsDir() {
    return new File(getMinecraftDir(), "jarMods");
  }

  public File getNativesDir() {
    return new File(getLibraryDir(), "natives");
  }

  public File getResourceDir() {
    return new File(getMinecraftDir(), "resourcepacks");
  }

  private int                  numLoadedValidate    = 0;
  private int                  totalLaunchSize      = 0;
  private List<FileDownloader> downloads            = new ArrayList<FileDownloader>();
  private int                  currentLaunchSize    = 0;
  private int                  lastDownloadProgress = 0;
  private int                  numLoadedDownloads   = 0;

  public void addValidatedFile(ProgressPanel panel, int fileSize, boolean shouldExtract) {
    numLoadedValidate++;
    panel.stateChanged("Validating Mod Links", (int) (((double) numLoadedValidate / (double) (downloads.size())) * 100D));
    totalLaunchSize += ((double) fileSize * (shouldExtract ? 2D : 1D));
  }

  public boolean hasOptionalMod(String name) {
    for (String s : getToken().modpackOptionalMods) {
      if (s.equalsIgnoreCase(name)) { return true; }
    }
    return false;
  }

  public void addToOrder(String name) {
    if (!jarOrder.isEmpty()) jarOrder += ",";
    jarOrder += name;
  }

  public void prepDownload() {
    mkdirs();
    if (loader.isWholeDownload()) {
      downloads.add(new FileDownloader(this, getDownloadLocation(), getMinecraftDir().getAbsolutePath(), true, false));
    }
    if (!modList.isEmpty()) {
      for (Mod m : modList.values()) {
        if (m.getDownloadUrl() == null || m.getDownloadUrl().isEmpty()) continue;
        switch (m.getType()) {
          default:
          case optionalMod:
          case mod:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getModsDir().getAbsolutePath(), false, false));
            break;
          case config:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getConfigDir().getAbsolutePath(), true, false));
            break;
          case resourcePack:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getResourceDir().getAbsolutePath(), false, false));
            break;
          case library:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getLibraryDir().getAbsolutePath(), false, true));
            break;
          case global:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getMinecraftDir().getAbsolutePath(), true, false));
            break;
          case minecraft:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getLibraryDir().getAbsolutePath(), "minecraft.jar", true));
            break;
          case forge:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getJarModsDir().getAbsolutePath(), false, true));
            break;
          case natives:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), getNativesDir().getAbsolutePath(), true, false));
            break;
          case resource:
            downloads.add(new FileDownloader(this, m.getDownloadUrl(), m.getInfoUrl(), m.getName(), false));
            break;
        }
      }
    }
    downloads.add(new FileDownloader(this, "http://optifine.net/downloadx?f=OptiFine_1.7.10_HD_U_B4.jar&x=cfc05b59db9a9a2b9fc6125cada321cc", getModsDir().getAbsolutePath(), "OptiFine_1.7.10_HD_A4.jar", false));
    downloads.add(new FileDownloader(this, "http://files.player.to/fastcraft-1.16.jar", getModsDir().getAbsolutePath(), false));
  }

  public boolean isFinishedDownloading() {
    return downloads.size() <= numLoadedDownloads;
  }

  public void addLoadedDownload() {
    numLoadedDownloads++;
  }

  public List<Runnable> loadFileSize(ProgressPanel panel) {
    List<Runnable> mods = new ArrayList<Runnable>();

    for (FileDownloader res : downloads) {
      if (res.shouldDownload()) {
        mods.add(res.loadFileSize(this, panel));
      }
    }
    return mods;
  }

  public boolean download(final ProgressPanel panel) throws IOException {
    prepDownload();
    new Thread() {
      public void run() {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        for (Runnable r : loadFileSize(panel)) {
          pool.submit(r);
        }
        pool.shutdown();
        try {
          pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        pool = Executors.newFixedThreadPool(10);
        try {
          for (Runnable r : downloadFiles(panel)) {
            pool.submit(r);
          }
        } catch (IOException e1) {
          e1.printStackTrace();
        }

        pool.shutdown();
        try {
          pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

          pool = Executors.newFixedThreadPool(10);
          if (loader instanceof TechnicServerLoader) {
            File binDir = new File(getMinecraftDir(), "bin");
            File modpackFile = new File(binDir, "modpack.jar");
            try {
              co.forsaken.projectindigo.utils.FileUtils.unzip(modpackFile, binDir);
              FileUtils.copyFile(new File(binDir, "version.json"), new File(getMinecraftDir(), "version.json"));
              FileUtils.deleteDirectory(binDir);
            } catch (IOException e) {
              e.printStackTrace();
            }
          }

          if (loader instanceof FtbServerLoader || loader instanceof TechnicServerLoader) {
            try {
              String packName = "pack.json";
              if (loader instanceof TechnicServerLoader) {
                packName = "version.json";
              }
              PackToken token = (PackToken) (new Gson()).fromJson(FileUtils.readFileToString(new File(getMinecraftDir(), packName)), PackToken.class);
              Artifact a;
              for (Library lib : token.libraries) {
                a = new Artifact(lib.name);
                String dir = getLibraryDir().getAbsolutePath();
                if (lib.name.toLowerCase().contains("minecraftforge")) {
                  dir = getJarModsDir().getAbsolutePath();
                  if (!lib.name.substring(lib.name.lastIndexOf(":") + 1).equals("universal")) {
                    lib.name += ":universal";
                    a = new Artifact(lib.name);
                  }
                }
                if (lib.url != null) {
                  pool.submit(new FileDownloader(Server.this, lib.url + a.getDownloadUrl(), dir, false).download(Server.this, panel));
                } else {
                  pool.submit(new FileDownloader(Server.this, ServerLoader.MOJANG_LIBS_BASE + a.getDownloadUrl(), dir, false).download(Server.this, panel));
                }
              }
            } catch (JsonSyntaxException e) {
              e.printStackTrace();
            } catch (IOException e) {
              e.printStackTrace();
            }
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
          }

          try {
            new File(getMinecraftDir(), "servers.dat").delete();
            getLockFile().createNewFile();
            co.forsaken.projectindigo.utils.FileUtils.writeStringToFile(getToken().version, getLockFile());
          } catch (IOException e) {
            e.printStackTrace();
          }
          panel.getMainPanel().switchPage(1);
          launch(panel.getMainPanel());
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      }
    }.start();
    return true;
  }

  private List<Runnable> downloadFiles(ProgressPanel panel) throws IOException {
    List<Runnable> mods = new ArrayList<Runnable>();
    for (FileDownloader download : downloads) {
      if (download.shouldDownload()) {
        mods.add(download.download(this, panel));
      }
    }
    return mods;
  }

  private int parseVersion(String version) {
    Pattern pat = Pattern.compile("\\.");
    Matcher match = pat.matcher(version);
    if (match.find()) {
      version = match.replaceAll("");
    }
    return Integer.parseInt(version);
  }

  public void cleanupLibs() {
    List<File> filesToRemove = new ArrayList<File>();
    File[] files = getLibraryDir().listFiles();
    for (File f : files) {
      if (f.isDirectory()) continue;
      for (File f2 : getLibraryDir().listFiles()) {
        if (f2.isDirectory()) continue;
        if (f.getName().equalsIgnoreCase("minecraft.jar") || f2.getName().equalsIgnoreCase("minecraft.jar") || f.getName().equals(f2.getName())) continue;
        if (f.getName().substring(0, f.getName().lastIndexOf('-')).equalsIgnoreCase(f2.getName().substring(0, f2.getName().lastIndexOf('-')))) {
          if (parseVersion(f2.getName().substring(f2.getName().lastIndexOf('-') + 1, f2.getName().lastIndexOf("."))) < parseVersion(f.getName().substring(f.getName().lastIndexOf('-') + 1, f.getName().lastIndexOf(".")))) {
            filesToRemove.add(f2);
            break;
          }
        }
      }
    }
    for (File f : filesToRemove) {
      f.delete();
    }
  }

  public void launch(final MainPanel panel) {
    cleanupLibs();
    Thread launcher = new Thread() {
      public void run() {
        try {
          long start = System.currentTimeMillis();

          LogManager.info("Launching pack " + getToken().friendlyName + " " + getToken().version + " for " + "Minecraft 1.7.10");
          IndigoLauncher._launcher.setVisible(false);
          Process process = null;
          try {
            process = MinecraftLauncher.launchMinecraft(Server.this, ((LoginPanel) panel.getPanel(0)).getLoginResponse(), ((SettingsPanel) panel.getPanel(3)).getSettings());
          } catch (IOException ex) {
            ex.printStackTrace();
          }
          InputStream is = process.getInputStream();
          InputStreamReader isr = new InputStreamReader(is);
          BufferedReader br = new BufferedReader(isr);
          String line;
          while ((line = br.readLine()) != null) {
            LogManager.minecraft(line);
          }
          long end = System.currentTimeMillis();

          int exitValue = 0; // Assume we exited fine
          try {
            exitValue = process.exitValue(); // Try to get the real exit value
          } catch (IllegalThreadStateException e) {
            e.printStackTrace();
            process.destroy(); // Kill the process
          }
          IndigoLauncher._launcher.setVisible(true);

        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    };
    launcher.start();
  }

  public int getDownloadProgress() {
    int prog = (int) ((double) ((double) currentLaunchSize / (double) totalLaunchSize) * 100);
    if (prog > 100) {
      prog = 100;
    } else if (prog < 0) {
      prog = 0;
    }
    return prog;
  }

  public void addDownloadSize(ProgressPanel panel, String type, String fileName, int amount) {
    currentLaunchSize += amount;
    lastDownloadProgress = getDownloadProgress();
    panel.stateChanged(type + " mod - " + fileName, lastDownloadProgress);
  }
}
