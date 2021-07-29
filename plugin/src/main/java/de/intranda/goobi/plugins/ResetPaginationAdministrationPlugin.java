package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class ResetPaginationAdministrationPlugin implements IAdministrationPlugin, IPushPlugin {

    @Getter
    private String title = "intranda_administration_reset_pagination";

    @Getter
    private int resultTotal = 0;

    @Getter
    private int resultProcessed = 0;

    @Getter
    @Setter
    private String filter;

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_reset_pagination.xhtml";
    }

    private List<Process> processes = new ArrayList<Process>();
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
    }

    /**
     * action method to run through all processes matching the filter
     */
    public void resetPagination() {

        // filter the list of all processes that should be affected
        String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
        List<Process> tempProcesses = ProcessManager.getProcesses("prozesse.titel", query);

        resultTotal = tempProcesses.size();
        resultProcessed = 0;
        processes = new ArrayList<Process>();
        
        Runnable run = () -> {
            try {
                long lastPush = System.currentTimeMillis();
                for (Process process : tempProcesses) {
                    resetPaginationForProcess(process);
                    processes.add(process);
                    resultProcessed++;
                    if (pusher != null && System.currentTimeMillis() - lastPush > 2000) {
                        lastPush = System.currentTimeMillis();
                        pusher.send("update");
                    }
                }

                Thread.sleep(200);
                if (pusher != null) {
                    pusher.send("update");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        new Thread(run).start();
    }

    private Prefs prefs;
    private Process process;

    public boolean resetPaginationForProcess(Process inProcess) {
        process = inProcess;
        prefs = process.getRegelsatz().getPreferences();
        Fileformat ff = null;
        try {
            ff = process.readMetadataFile();
        } catch (ReadException | PreferencesException | SwapException | DAOException | WriteException | IOException
                | InterruptedException e) {
            log.error(e);
            Helper.setFehlerMeldung(e);
            return false;
        }

        try {

            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct rootElement = dd.getLogicalDocStruct();
            DocStruct physicalElement = dd.getPhysicalDocStruct();
            try {
                createPagination(physicalElement, rootElement, dd);
            } catch (Exception e) {
                log.error(e);
            }
        } catch (PreferencesException e) {
            log.error(e);
            Helper.setFehlerMeldung(e);
            return false;
        }

        try {
            process.writeMetadataFile(ff);
        } catch (PreferencesException | SwapException | DAOException | WriteException | IOException
                | InterruptedException e) {
            Helper.setFehlerMeldung(e);
            return false;
        }
        return true;
    }

    public void createPagination(DocStruct physicaldocstruct, DocStruct logical, DigitalDocument dd) throws Exception {
        MetadataType MDTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");

        // create physical tree only if it does not exist already
        if (physicaldocstruct == null) {
            DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
            physicaldocstruct = dd.createDocStruct(dst);
            dd.setPhysicalDocStruct(physicaldocstruct);
        }

        // check for valid filepath
        try {
            List<? extends Metadata> filepath = physicaldocstruct.getAllMetadataByType(MDTypeForPath);
            Metadata mdForPath;
            if (filepath == null || filepath.isEmpty()) {
                mdForPath = new Metadata(MDTypeForPath);
                physicaldocstruct.addMetadata(mdForPath);
            } else {
                mdForPath = filepath.get(0);
            }
            mdForPath.setValue("file://" + process.getImagesTifDirectory(false));
        } catch (Exception e) {
            log.error(e);
        }

        // retrieve existing pages/images
        File imagesDirectory = new File(process.getImagesTifDirectory(false));
        if (imagesDirectory.isDirectory()) {
            List<File> imageFiles = Arrays.asList(imagesDirectory.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith("tif") || name.toLowerCase().endsWith("tiff")
                            || name.toLowerCase().endsWith("jpg") || name.toLowerCase().endsWith("jpeg")
                            || name.toLowerCase().endsWith("jp2") || name.toLowerCase().endsWith("png");
                }
            }));

            Collections.sort(imageFiles);

            int pageNo = 0;
            for (File file : imageFiles) {
                pageNo++;
                addPage(physicaldocstruct, logical, dd, file, pageNo);
            }
        }

    }

    private void addPage(DocStruct physicaldocstruct, DocStruct logical, DigitalDocument dd, File imageFile, int pageNo)
            throws TypeNotAllowedForParentException, IOException, InterruptedException, SwapException, DAOException {
        DocStructType newPage = prefs.getDocStrctTypeByName("page");
        DocStruct dsPage = dd.createDocStruct(newPage);
        try {
            // physical page no
            physicaldocstruct.addChild(dsPage);
            MetadataType mdt = prefs.getMetadataTypeByName("physPageNumber");
            Metadata mdTemp = new Metadata(mdt);
            mdTemp.setValue(String.valueOf(pageNo));
            dsPage.addMetadata(mdTemp);

            // logical page no
            mdt = prefs.getMetadataTypeByName("logicalPageNumber");
            mdTemp = new Metadata(mdt);
            mdTemp.setValue("uncounted");
            dsPage.addMetadata(mdTemp);
            logical.addReferenceTo(dsPage, "logical_physical");

            // image name
            ContentFile cf = new ContentFile();
            cf.setLocation("file://" + imageFile.getAbsolutePath());
            dsPage.addContentFile(cf);

        } catch (TypeNotAllowedAsChildException e) {
            log.error(e);
        } catch (MetadataTypeNotAllowedException e) {
            log.error(e);
        }
    }
    
    /**
     * Get a given maximum of processes 
     * 
     * @param inMax
     * @return
     */
    public List<Process> resultListLimited(int inMax) {
        if (inMax > processes.size()) {
            return processes;
        } else {
            return processes.subList(0, inMax);
        }
    }
}
