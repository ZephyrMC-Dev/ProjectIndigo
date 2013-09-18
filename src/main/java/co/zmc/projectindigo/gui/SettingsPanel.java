package co.zmc.projectindigo.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import co.zmc.projectindigo.data.log.Logger;
import co.zmc.projectindigo.gui.components.Button;
import co.zmc.projectindigo.gui.components.DirectorySelector;
import co.zmc.projectindigo.gui.components.Label;
import co.zmc.projectindigo.gui.components.RoundedBox;
import co.zmc.projectindigo.gui.components.TextBox;
import co.zmc.projectindigo.utils.DirectoryLocations;
import co.zmc.projectindigo.utils.Settings;

@SuppressWarnings("serial")
public class SettingsPanel extends BasePanel {

    private RoundedBox _bg;
    private Button     _continueBtn;
    private Settings   _settings;

    public SettingsPanel(MainPanel mainPanel) {
        super(mainPanel, 2);
    }

    public static final String INSTALL_PATH     = "install_folder";
    public static final String MAX_RAM          = "maximum_ram";
    public static final String MAX_PERM_SIZE    = "maximum_perm_size";
    public static final String JAVA_PARAMS      = "additional_java_params";
    public static final String WINDOW_SIZE      = "minecraft_window_size";
    public static final String WINDOW_POSITION  = "minecraft_window_position";
    public static final String WINDOW_MAXIMIZED = "minecraft_window_maximized";

    private Label              _installDirLbl;
    private TextBox            _installDirBox;
    private Button             _installDirBtn;
    private Label              _maxRamLbl;
    private Label              _maxRamAmtLbl;
    private JSlider            _maxRamSlider;

    public void initComponents() {
        loadSettings();

        _bg = new RoundedBox(MainPanel.BORDER_COLOUR);
        _bg.setBounds((getWidth() - (getWidth() - 50)) / 2, (getHeight() - (getHeight() - 50)) / 2, getWidth() - 50, getHeight() - 50);

        _continueBtn = new Button(this, "Launch");
        _continueBtn.setBounds(_bg.getWidth() - (150 - 10), _bg.getHeight() - (25 - 10), 150, 25);
        _continueBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                launch();
            }
        });

        _installDirBox = new TextBox(this, _settings.get(Settings.INSTALL_PATH));
        _installDirBox.setEnabled(false);
        _installDirBox.setBounds(((getWidth() - (getWidth() - 300)) / 2) - (150 / 2), (50 / 2) + 40, (getWidth() - 300), 35);

        _installDirBtn = new Button(this, "...");
        _installDirBtn.addActionListener(new DirectorySelector(SettingsPanel.this));
        _installDirBtn.setBounds(_installDirBox.getX() + _installDirBox.getWidth() + 50, _installDirBox.getY(), 100, _installDirBox.getHeight());

        _installDirLbl = new Label(this, "Install Location: ");
        _installDirLbl.setBounds(_installDirBox.getX(), _installDirBox.getY() - 40, _installDirBox.getWidth(), _installDirBox.getHeight());

        _installDirBox = new TextBox(this, _settings.get(Settings.INSTALL_PATH));
        _installDirBox.setEnabled(false);
        _installDirBox.setBounds(((getWidth() - (getWidth() - 300)) / 2) - (150 / 2), (50 / 2) + 40, (getWidth() - 300), 35);

        _installDirBtn = new Button(this, "...");
        _installDirBtn.addActionListener(new DirectorySelector(SettingsPanel.this));
        _installDirBtn.setBounds(_installDirBox.getX() + _installDirBox.getWidth() + 50, _installDirBox.getY(), 100, _installDirBox.getHeight());

        _installDirLbl = new Label(this, "Install Location: ");
        _installDirLbl.setBounds(_installDirBox.getX(), _installDirBox.getY() - 40, _installDirBox.getWidth(), _installDirBox.getHeight());

        _maxRamAmtLbl = new Label(this, _settings.get(Settings.MAX_RAM));

        long ram = 0;
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        Method m;
        try {
            m = operatingSystemMXBean.getClass().getDeclaredMethod("getTotalPhysicalMemorySize");
            m.setAccessible(true);
            Object value = m.invoke(operatingSystemMXBean);
            if (value != null) {
                ram = Long.valueOf(value.toString()) / 1024 / 1024;
            } else {
                Logger.logWarn("Could not get RAM Value");
                ram = 4096;
            }
        } catch (Exception e) {
            Logger.logError(e.getMessage(), e);
        }

        _maxRamSlider = new JSlider();

        _maxRamSlider.setSnapToTicks(true);
        _maxRamSlider.setMajorTickSpacing(256);
        _maxRamSlider.setMinorTickSpacing(256);
        _maxRamSlider.setMinimum(256);
        String vmType = System.getProperty("sun.arch.data.model");
        if (vmType != null) {
            if (vmType.equals("64")) {
                _maxRamSlider.setMaximum((int) ram);
            } else if (vmType.equals("32")) {
                if (ram < 1024) {
                    _maxRamSlider.setMaximum((int) ram);
                } else {
                    _maxRamSlider.setMaximum(1024);
                }
            }
        }
        int ramMax = (Integer.parseInt(_settings.get(Settings.MAX_RAM)) > _maxRamSlider.getMaximum()) ? _maxRamSlider.getMaximum() : Integer
                .parseInt(_settings.get(Settings.MAX_RAM));
        _maxRamSlider.setValue(ramMax);
        _maxRamAmtLbl.setText(getAmount());
        _maxRamSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                _maxRamAmtLbl.setText(getAmount());
            }
        });
        add(_maxRamSlider, 0);
        _maxRamLbl = new Label(this, "Max RAM Allocation: ");
        _maxRamSlider.setBounds(((getWidth() - (getWidth() - 300)) / 2) - (150 / 2), _installDirBox.getY() + 50 + 40, (getWidth() - 300), 35);
        _maxRamAmtLbl.setBounds(_maxRamSlider.getX() + _maxRamSlider.getWidth() + 50, _maxRamSlider.getY(), 100, _maxRamSlider.getHeight());
        _maxRamLbl.setBounds(_maxRamSlider.getX(), _maxRamSlider.getY() - 40, _maxRamSlider.getWidth(), _maxRamSlider.getHeight());;

        add(_bg);

    }

    private String getAmount() {
        int ramMax = _maxRamSlider.getValue();
        return (ramMax >= 1024) ? Math.round((ramMax / 256) / 4) + "." + (((ramMax / 256) % 4) * 25) + " GB" : ramMax + " MB";
    }

    public void setInstallFolderText(String path) {
        _installDirBox.setText(path);
    }

    public Settings getSettings() {
        return _settings;
    }

    public void loadSettings() {
        _settings = new Settings();
    }

    public void launch() {
        _settings.set(Settings.INSTALL_PATH, _installDirBox.getText());
        _settings.set(Settings.MAX_RAM, _maxRamSlider.getValue() + "");

        _settings.save();
        DirectoryLocations.updateServerDir();
        ((ServerPanel) _mainPanel.getPanel(1)).getServerManager().getServer(((ServerPanel) _mainPanel.getPanel(1))._selectedServer).updateDir();

        ((ServerPanel) getMainPanel().getPanel(1)).launchServer(_settings);

    }
}
