package de.intranda.goobi.plugins;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;

import de.sub.goobi.config.ConfigPlugins;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class ResetPaginationAdministrationPlugin implements IAdministrationPlugin {

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

    /**
     * Constructor
     */
    public ResetPaginationAdministrationPlugin() {
        log.info("Sample admnistration plugin started");
        value = ConfigPlugins.getPluginConfig(title).getString("value", "default value");
    }
    
    public void resetPagination() {
    	for (int i = 0; i < 100; i++) {
			counter = i;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
    }
}
