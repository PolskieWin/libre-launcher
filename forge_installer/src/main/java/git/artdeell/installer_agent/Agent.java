package git.artdeell.installer_agent;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public class Agent implements AWTEventListener {
    private boolean forgeWindowHandled = false;
    private final boolean optiFineInstallation;
    private final Timer componentTimer = new Timer();

    public Agent(boolean of) {
        this.optiFineInstallation = of;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        WindowEvent windowEvent = (WindowEvent) event;
        Window window = windowEvent.getWindow();
        if(windowEvent.getID() != WindowEvent.WINDOW_OPENED) return;
        if(forgeWindowHandled && window instanceof JDialog) { // expecting a new dialog
            handleDialog(window);
            return;
        }
        if(!forgeWindowHandled) { // false at startup, so we will handle the first window as the Forge one
            forgeWindowHandled =  handleMainWindow(window);
            checkComponentTimer();
        }
    }

    public void checkComponentTimer() {
        if(forgeWindowHandled) {
            componentTimer.cancel();
            componentTimer.purge();
            return;
        }
        componentTimer.schedule(new ComponentTimeoutTask(), 30000);

    }

    public boolean handleMainWindow(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new MainWindowFilter());
        AbstractButton okButton = null;
        for(Component component : components) {
            if(component instanceof AbstractButton) {
                AbstractButton abstractButton = (AbstractButton) component;
                abstractButton = optiFineInstallation ?
                        handleOptiFineButton(abstractButton) :
                        handleForgeButton(abstractButton);
                if(abstractButton != null) okButton = abstractButton;
            }
        }
        if(okButton == null) {
            System.out.println("Failed to set all the UI components, wil try again in the next window");
            return false;
        }else{
            EventQueue.invokeLater(okButton::doClick); // do that after forge actually builds its window, otherwise we set the path too fast
            return true;
        }
    }


    public AbstractButton handleForgeButton(AbstractButton abstractButton) {
        String text = abstractButton.getText();
        if (text == null) return null;
        String cleanText = text.replaceAll("<[^>]*>", "").trim();
        String lower = cleanText.toLowerCase();

        if (lower.contains("client") && (abstractButton.getClass().getName().contains("Radio") || abstractButton.getClass().getName().contains("Check"))) {
            abstractButton.doClick();
            return null;
        }

        if (lower.equals("install client")) {
            if (abstractButton.getClass().getName().contains("Radio") || abstractButton.getClass().getName().contains("Check")) {
                abstractButton.doClick();
                return null;
            } else {
                return abstractButton;
            }
        }

        if (lower.equals("proceed") || lower.equals("ok") || lower.equals("install") || lower.equals("finish") || lower.equals("done") || lower.equals("next")) {
            return abstractButton;
        }
        return null;
    }

    public AbstractButton handleOptiFineButton(AbstractButton abstractButton) {
        String text = abstractButton.getText();
        if (text == null) return null;
        String cleanText = text.replaceAll("<[^>]*>", "").trim();
        if (cleanText.equalsIgnoreCase("Install") || cleanText.equalsIgnoreCase("OK")) {
            return abstractButton;
        }
        return null;
    }

    public void handleDialog(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new DialogFilter());
        for (Component component : components) {
            if (component instanceof JOptionPane) {
                JOptionPane optionPane = (JOptionPane) component;
                Object msgObj = optionPane.getMessage();
                String msg = msgObj != null ? msgObj.toString().toLowerCase() : "";
                int msgType = optionPane.getMessageType();

                if (msgType == JOptionPane.ERROR_MESSAGE || msg.contains("error") || msg.contains("fail")) {
                    System.err.println("Installation dialog reported error: " + msg);
                    System.exit(1);
                } else if (msgType == JOptionPane.INFORMATION_MESSAGE || msgType == JOptionPane.PLAIN_MESSAGE || msg.contains("success") || msg.contains("complete") || msg.contains("finish") || msg.contains("installed")) {
                    System.out.println("The install was successful!");
                    System.exit(0);
                }
            }
        }
    }

    public void insertAllComponents(List<Component> components, Container parent, ComponentFilter filter) {
        int componentCount = parent.getComponentCount();
        for(int i = 0; i < componentCount; i++) {
            Component component = parent.getComponent(i);
            if(filter.checkComponent(component)) components.add(component);
            if(component instanceof Container) {
                insertAllComponents(components, (Container) component, filter);
            }
        }
    }

    public static void premain(String args, Instrumentation inst) {
        boolean optifine = false;
        if(args != null ) {
            optifine = args.contains("OF");
        }
        Agent agent = new Agent(optifine);
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(agent,
                        AWTEvent.WINDOW_EVENT_MASK);
    }
}
