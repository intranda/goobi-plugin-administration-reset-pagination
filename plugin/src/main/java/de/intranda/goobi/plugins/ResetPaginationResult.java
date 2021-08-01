package de.intranda.goobi.plugins;

import org.goobi.beans.Process;

import lombok.Data;

@Data
public class ResetPaginationResult {
	private Process process;
	private String status = "OK";
	private String message = "";
}
