package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
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
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
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

	private List<ResetPaginationResult> results = new ArrayList<ResetPaginationResult>();
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

		// filter the list of all processes that should be affected
		String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
		List<Process> tempProcesses = ProcessManager.getProcesses("prozesse.titel", query);

		resultTotal = tempProcesses.size();
		resultProcessed = 0;
		results = new ArrayList<ResetPaginationResult>();

		Runnable run = () -> {
			try {
				long lastPush = System.currentTimeMillis();
				for (Process process : tempProcesses) {
					//Thread.sleep(800);
					ResetPaginationResult r = new ResetPaginationResult();
					r.setProcess(process);
					try {
						resetPaginationForProcess(process);
					} catch (Exception e) {
						r.setStatus("ERROR");
						r.setMessage(e.getMessage());
						log.error("Error while executing the pagination reset", e);
					}
					results.add(r);
					resultProcessed++;
					if (pusher != null && System.currentTimeMillis() - lastPush > 500) {
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

	/**
	 * Reset the pagination for a given process (magic copied over from the
	 * METS-Editor)
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
		List<String> allTifFolders = new ArrayList<String>();
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

		if (!ConfigurationHelper.getInstance().getProcessImagesFallbackDirectoryName().equals("")) {
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
	 * Get a given maximum of processes
	 * 
	 * @param inMax
	 * @return
	 */
	public List<ResetPaginationResult> resultListLimited(int inMax) {
		if (inMax > results.size()) {
			return results;
		} else {
			return results.subList(0, inMax);
		}
	}
	
	/**
	 * get the progress in percent to render a progress bar
	 * @return progress as percentage
	 */
	public int getProgress() {
		return 100 * resultProcessed / resultTotal;	
	}

}
