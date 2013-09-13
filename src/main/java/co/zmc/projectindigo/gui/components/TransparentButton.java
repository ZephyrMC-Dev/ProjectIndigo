package co.zmc.projectindigo.gui.components;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JComponent;

import co.zmc.projectindigo.IndigoLauncher;

@SuppressWarnings("serial")
public class TransparentButton extends JButton implements MouseListener {
    private boolean clicked            = false;
    private float   _transparencyLevel = 1F;
    private boolean _isToggle          = false;
    private boolean _isHover           = false;
    private Color   _rolloverColor;

    public TransparentButton(JComponent frame, String label, float transparencyLevel, Color rolloverColor) {
        this(frame, label, transparencyLevel);
        _rolloverColor = rolloverColor;
    }

    public TransparentButton(JComponent frame, String label, float transparencyLevel, boolean isToggle) {
        this(frame, label, transparencyLevel);
        _isToggle = isToggle;
    }

    public TransparentButton(JComponent frame, String label, float transparencyLevel) {
        _transparencyLevel = transparencyLevel;
        setText(label);
        setBackground(Color.WHITE);
        _rolloverColor = Color.GRAY;
        addMouseListener(this);
        setFont(IndigoLauncher.getMinecraftFont(14));
        frame.add(this, 0);
        this.setRolloverEnabled(true);
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Color old = g2d.getColor();
        Composite comp = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, _transparencyLevel);
        g2d.setComposite(comp);

        g2d.setColor(this.clicked ? Color.BLACK : (_isHover ? _rolloverColor : getBackground()));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(this.clicked ? (_isHover ? _rolloverColor : getBackground()) : Color.BLACK);
        g2d.setFont(getFont());
        int width = g2d.getFontMetrics().stringWidth(getText());
        g2d.drawString(getText(), (getWidth() - width) / 2, getFont().getSize() + 4);

        g2d.setColor(old);
        g2d.dispose();
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.clicked = !enabled;
        repaint();
    }

    public boolean isClicked() {
        return clicked;
    }

    public void mouseClicked(MouseEvent e) {
        if (_isToggle) {
            this.clicked = !this.clicked;
        }
    }

    public void mousePressed(MouseEvent e) {
        if (!_isToggle) {
            this.clicked = true;
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (!_isToggle) {
            this.clicked = false;
        }
    }

    public void mouseEntered(MouseEvent e) {
        if (!_isHover) {
            _isHover = true;
        }
    }

    public void mouseExited(MouseEvent e) {
        if (_isHover) {
            _isHover = false;
        }
    }
}