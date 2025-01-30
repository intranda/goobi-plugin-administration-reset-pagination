package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.managedbeans.ProcessBean;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.metadaten.MetadatenImagesHelper;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.dl.Reference;

@PluginImplementation
@Log4j2
public class ResetPaginationAdministrationPlugin implements IAdministrationPlugin, IPushPlugin {

    private static final long serialVersionUID = -2909331469974333390L;

    @Getter
    private String title = "intranda_administration_reset_pagination";

    @Getter
    @Setter
    private int limit = 10;

    @Getter
    private int resultTotal = 0;

    @Getter
    private int resultProcessed = 0;

    @Getter
    private boolean run = false;

    @Getter
    @Setter
    private String filter;

    @Getter
    private transient List<ResetPaginationResult> resultsLimited = new ArrayList<>();
    private transient List<ResetPaginationResult> results = new ArrayList<>();
    private PushContext pusher;

    /**
     * Constructor
     */
    public ResetPaginationAdministrationPlugin() {
        log.info("Reset pagination admnistration plugin started");
        filter = ConfigPlugins.getPluginConfig(title).getString("filter", "");
    }

    /**
     * action method to run through all processes matching the filter
     */
    public void execute() {
        run = true;

        // filter the list of all processes that should be affected
        String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
        List<Integer> tempProcesses = ProcessManager.getIdsForFilter(query);

        resultTotal = tempProcesses.size();
        resultProcessed = 0;
        results = new ArrayList<>();
        resultsLimited = new ArrayList<>();

        Runnable runnable = () -> {
            try {
                long lastPush = System.currentTimeMillis();
                for (Integer processId : tempProcesses) {
                    Process process = ProcessManager.getProcessById(processId);
                    Thread.sleep(1000);
                    if (!run) {
                        break;
                    }
                    ResetPaginationResult r = new ResetPaginationResult();
                    r.setTitle(process.getTitel());
                    r.setId(process.getId());
                    try {
                        resetPaginationForProcess(process);
                    } catch (Exception e) {
                        r.setStatus("ERROR");
                        r.setMessage(e.getMessage());
                        log.error("Error while executing the pagination reset", e);
                    }
                    results.add(0, r);
                    if (results.size() > limit) {
                        resultsLimited = new ArrayList<>(results.subList(0, limit));
                    } else {
                        resultsLimited = new ArrayList<>(results);
                    }
                    resultProcessed++;
                    if (pusher != null && System.currentTimeMillis() - lastPush > 1000) {
                        lastPush = System.currentTimeMillis();
                        pusher.send("update");
                    }
                }

                run = false;
                Thread.sleep(200);
                if (pusher != null) {
                    pusher.send("update");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        new Thread(runnable).start();
    }

    /**
     * Reset the pagination for a given process (magic copied over from the METS-Editor)
     * 
     * @param inProcess
     * @return
     * @throws Exception
     */
    public void resetPaginationForProcess(Process inProcess) throws Exception {
        Prefs prefs = inProcess.getRegelsatz().getPreferences();
        Fileformat ff = inProcess.readMetadataFile();
        DigitalDocument dd = ff.getDigitalDocument();
        DocStruct rootElement = dd.getLogicalDocStruct();
        DocStruct physical = dd.getPhysicalDocStruct();
        if (physical != null && physical.getAllChildren() != null) {
            List<DocStruct> pages = physical.getAllChildren();
            for (DocStruct page : pages) {
                dd.getFileSet().removeFile(page.getAllContentFiles().get(0));

                List<Reference> refs = new ArrayList<>(page.getAllFromReferences());
                for (ugh.dl.Reference ref : refs) {
                    ref.getSource().removeReferenceTo(page);
                }
                if (page.getAllChildren() != null) {
                    for (DocStruct area : page.getAllChildren()) {
                        List<Reference> arearefs = new ArrayList<>(area.getAllFromReferences());
                        for (ugh.dl.Reference ref : arearefs) {
                            ref.getSource().removeReferenceTo(area);
                        }
                    }
                }
            }
        }
        while (physical.getAllChildren() != null && !physical.getAllChildren().isEmpty()) {
            physical.removeChild(physical.getAllChildren().get(0));
        }
        createPagination(inProcess, prefs, physical, rootElement, dd);
        inProcess.writeMetadataFile(ff);
    }

    /**
     * create a new pagination (magic copied over from the METS-Editor)
     * 
     * @param inProcess
     * @param prefs
     * @param physicaldocstruct
     * @param logical
     * @param dd
     * @throws Exception
     */
    public void createPagination(Process inProcess, Prefs prefs, DocStruct physicaldocstruct, DocStruct logical,
            DigitalDocument dd) throws Exception {
        List<String> allTifFolders = new ArrayList<>();
        Path dir = Paths.get(inProcess.getImagesDirectory());

        List<String> verzeichnisse = StorageProvider.getInstance().listDirNames(dir.toString());
        for (int i = 0; i < verzeichnisse.size(); i++) {
            allTifFolders.add(verzeichnisse.get(i));
        }
        String currentTifFolder = allTifFolders.get(0);

        Path thumbsDir = Paths.get(inProcess.getThumbsDirectory());
        if (StorageProvider.getInstance().isDirectory(thumbsDir)) {
            List<String> thumbDirs = StorageProvider.getInstance().listDirNames(thumbsDir.toString());
            for (String thumbDirName : thumbDirs) {
                String matchingImageDir = inProcess.getMatchingImageDir(thumbDirName);
                if (!allTifFolders.contains(matchingImageDir)) {
                    allTifFolders.add(matchingImageDir);
                }
            }
        }

        if (!"".equals(ConfigurationHelper.getInstance().getProcessImagesFallbackDirectoryName())) {
            String foldername = ConfigurationHelper.getInstance().getProcessImagesFallbackDirectoryName();
            for (String directory : allTifFolders) {
                if (directory.equals(foldername)) {
                    currentTifFolder = directory;
                    break;
                }
            }
        }

        if (StringUtils.isBlank(currentTifFolder) && !allTifFolders.isEmpty()) {
            currentTifFolder = Paths.get(inProcess.getImagesTifDirectory(true)).getFileName().toString();
            if (!allTifFolders.contains(currentTifFolder)) {
                currentTifFolder = allTifFolders.get(0);
            }
        }

        MetadatenImagesHelper imagehelper = new MetadatenImagesHelper(prefs, dd);
        imagehelper.createPagination(inProcess, currentTifFolder);

        // added new
        if (ConfigurationHelper.getInstance().isMetsEditorEnableImageAssignment()) {
            if (logical.getType().isAnchor()) {
                if (logical.getAllChildren() != null && logical.getAllChildren().size() > 0) {
                    logical = logical.getAllChildren().get(0);
                } else {
                    return;
                }
            }

            if (logical.getAllChildren() != null) {
                for (DocStruct child : logical.getAllChildren()) {
                    List<Reference> childRefs = child.getAllReferences("to");
                    for (Reference toAdd : childRefs) {
                        boolean match = false;
                        for (Reference ref : logical.getAllReferences("to")) {
                            if (ref.getTarget().equals(toAdd.getTarget())) {
                                match = true;
                                break;
                            }
                        }
                        if (!match) {
                            logical.getAllReferences("to").add(toAdd);
                        }
                    }
                }
            }
        }
    }

    /**
     * show the result inside of the process list
     * 
     * @return
     */
    public String showInProcessList(String limit) {
        StringBuilder search = new StringBuilder("\"id:");
        for (ResetPaginationResult r : results) {
            if (limit.isEmpty() || limit.equals(r.getStatus())) {
                search.append(r.getId()).append(" ");
            }
        }
        search.append("\"");
        ProcessBean processBean = Helper.getBeanByClass(ProcessBean.class);
        processBean.setFilter(search.toString());
        processBean.setModusAnzeige("aktuell");
        return processBean.FilterAlleStart();
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_reset_pagination.xhtml";
    }

    @Override
    public void setPushContext(PushContext pusher) {
        this.pusher = pusher;
    }

    /**
     * get the progress in percent to render a progress bar
     *
     * @return progress as percentage
     */
    public int getProgress() {
        return 100 * resultProcessed / resultTotal;
    }

    /**
     * stop further processing
     */
    public void cancel() {
        run = false;
    }
}
