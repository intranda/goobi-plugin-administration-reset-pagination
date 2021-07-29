package de.intranda.goobi.plugins;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class ResetPaginationAdministrationPlugin implements IAdministrationPlugin, IPushPlugin {

    @Getter
    private String title = "intranda_administration_reset_pagination";

    @Getter
    private String value;

    @Getter
    private int counter = 0;

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_reset_pagination.xhtml";
    }

    private PushContext pusher;

    @Override
    public void setPushContext(PushContext pusher) {
        this.pusher = pusher;
    }

    /**
     * Constructor
     */
    public ResetPaginationAdministrationPlugin() {
        log.info("Sample admnistration plugin started");
        value = ConfigPlugins.getPluginConfig(title).getString("value", "default value");
    }

    public void resetPagination() {
        Runnable run = () -> {
            try {
                for (int i = 0; i < 100; i++) {
                    counter = i;
                    Thread.sleep(100);
                    if (pusher != null && i % 3 == 0) {
                        pusher.send("update");
                    }
                    Thread.sleep(100);
                    if (pusher != null) {
                        pusher.send("update");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        new Thread(run).start();
    }
}
