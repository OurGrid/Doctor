package org.ourgrid.doctor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.ourgrid.broker.status.GridProcessStatusInfo;
import org.ourgrid.broker.status.JobStatusInfo;
import org.ourgrid.broker.status.TaskStatusInfo;
import org.ourgrid.broker.status.WorkerStatusInfo;
import org.ourgrid.common.executor.ExecutorResult;
import org.ourgrid.common.interfaces.to.JobsPackage;
import org.ourgrid.common.specification.job.JobSpecification;
import org.ourgrid.common.specification.main.CommonCompiler;
import org.ourgrid.common.specification.main.CommonCompiler.FileType;
import org.ourgrid.common.specification.main.CompilerException;
import org.ourgrid.doctor.component.DoctorAsyncApplicationClient;
import org.ourgrid.doctor.component.DoctorContextFactory;
import org.ourgrid.doctor.component.DoctorListener;

import br.edu.ufcg.lsd.commune.container.control.ControlOperationResult;
import br.edu.ufcg.lsd.commune.context.ModuleContext;
import br.edu.ufcg.lsd.commune.context.PropertiesFileParser;
import br.edu.ufcg.lsd.commune.network.xmpp.CommuneNetworkException;
import br.edu.ufcg.lsd.commune.processor.ProcessorStartException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Doctor {
	
	private static final int GET_STATUS_INITIAL_DELAY = 0;
	private static final Logger LOGGER = Logger.getLogger(Doctor.class);
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");
	
	private DoctorAsyncApplicationClient brokerDoctorClient;
	private List<Integer> jobsIds = new LinkedList<Integer>();
	private Map<Integer,String> jobsProperties = new HashMap<Integer,String>();
	private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> scheduledFuture;
	private Properties configuration;
	
	public Doctor() throws CommuneNetworkException, ProcessorStartException, IOException {
		
		String configurationFilePath = System.getenv("doctor.configuration");
		
		this.configuration = new Properties();
		this.configuration.load(new FileInputStream(configurationFilePath));
		
		File brokerProperties = new File(configuration.getProperty(Conf.BROKER_PROPERTIES_PATH));
		ModuleContext brokerClientContext = new DoctorContextFactory(
				new PropertiesFileParser(brokerProperties.getAbsolutePath())).createContext();
		brokerDoctorClient = new DoctorAsyncApplicationClient(brokerClientContext);
	}
	
	public void start() {
		addListeners();
	}
	
	private void addListeners() {
		this.brokerDoctorClient.addListener(new DoctorListener() {
			
			@Override
			public void doctorIsUp() {
				try {
					submitJobs();
				} catch (Exception e) {
					LOGGER.error("Failure while submitting jobs.", e);
				}
			}

			@Override
			public void hereIsJobStatus(JobsPackage jobsStatus) {
				hereIsCompleteJobStatus(jobsStatus);
			}
		});
	}
	
	private void submitJobs() throws IOException {
		
		String jobsDir = configuration.getProperty(Conf.JOBS_PATH);
		String sandboxDirPath = configuration.getProperty(Conf.SANDBOX_PATH);

		File sandboxDir = new File(sandboxDirPath);
		
		try {
			FileUtils.deleteDirectory(sandboxDir);
		} catch (Exception e) {
			// Best effort here
		}
		FileUtils.copyDirectory(new File(jobsDir), sandboxDir);
		
		CommonCompiler compiler = new CommonCompiler();
		if (sandboxDir.isDirectory()) {
			for (File jdfFile : sandboxDir.listFiles()) {
				if (jdfFile.isFile() && jdfFile.getName().endsWith(".jdf")) {
					JobSpecification theJob = null;
					try {
						compiler.compile(jdfFile.getAbsolutePath(), FileType.JDF);
						theJob = (JobSpecification) compiler.getResult().get(0);
					} catch (CompilerException e) {
						e.printStackTrace();
					}
					ControlOperationResult addJobResult = brokerDoctorClient.addJob(theJob);
					if (addJobResult.getErrorCause() == null) {
						Integer jobId = (Integer) addJobResult.getResult();
						if (jobId >= 1) {
							jobsIds.add(jobId);
							jobsProperties.put(jobId, jdfFile.getAbsolutePath().replace(".jdf", ".properties"));
						}
					}
				}
			}
		}
		String getStatusDelayStr = configuration.getProperty(Conf.BROKER_RETRIEVAL_INTERVAL);
		scheduledFuture = scheduledExecutor.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				brokerDoctorClient.getJobsStatus(jobsIds);
			}
		}, GET_STATUS_INITIAL_DELAY, Long.valueOf(getStatusDelayStr), TimeUnit.SECONDS);
	}

	private void hereIsCompleteJobStatus(JobsPackage jobsStatus) {
		for (JobStatusInfo jobStatus : jobsStatus.getJobs().values()) {
			if (jobStatus.isRunning()) {
				return;
			}
		}
		scheduledFuture.cancel(true);
		StringBuilder report = new StringBuilder();
		boolean succeeded = reportResults(jobsStatus, report);
		String reportBasePath = configuration.getProperty(Conf.REPORT_PATH);
		
		String reportFileName = "report-" + DATE_FORMAT.format(new Date()) + ".txt";
		
		try {
			IOUtils.write(report.toString(), new FileOutputStream(new File(reportBasePath, reportFileName)));
		} catch (IOException e) {
			LOGGER.error("Could not write report.", e);
		}
		
		if (!succeeded) {
			try {
				String reportBaseURL = configuration.getProperty(Conf.REPORT_URL);
				String reportLink = reportBaseURL + reportFileName;
				new EmailSender(configuration).send(
						"Doctor failed! See results at <a href='" + reportLink + "'>" + reportLink + "</a>.");
			} catch (MessagingException e1) {
				LOGGER.error("Could not send email.", e1);
			}
		}
		
		try {
			brokerDoctorClient.shutdown();
		} catch (CommuneNetworkException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private boolean reportResults(JobsPackage jobsStatus, StringBuilder report) {
		boolean allOk = true;
		for (JobStatusInfo jobStatus : jobsStatus.getJobs().values()) {
			allOk &= reportResults(jobStatus, report);
			addLine(report, "============================================");
		}
		return allOk;
	}

	private boolean reportResults(JobStatusInfo jobStatus, StringBuilder report) {
		addLine(report, "============JOB=============================");
		addLine(report, "Job " + jobStatus.getJobSpec().getLabel());
		addLine(report, "Status: " + JobStatusInfo.getState(jobStatus.getState()));
		addLine(report, "============TASKS===========================");
		for (TaskStatusInfo taskStatus : jobStatus.getTasks()) {
			addLine(report, "Task " + taskStatus.getTaskId());
			addLine(report, "Status: " + taskStatus.getState());
			addLine(report, "Spec: ");
			addLine(report, taskStatus.getSpec().toString());
			addLine(report, "============Replicas========================");
			for (GridProcessStatusInfo process : taskStatus.getGridProcesses()) {
				addLine(report, "Replica " + process.getId());
				addLine(report, "Status: " + process.getState());
				addLine(report, "Phase: " + process.getPhase());
				String executionErrorCause = process.getReplicaResult().getExecutionErrorCause();
				if (executionErrorCause != null) {
					addLine(report, "Erro cause: " + executionErrorCause);
				}
				WorkerStatusInfo allocation = process.getWorkerInfo();
				if (allocation != null) {
					addLine(report, "Allocated to: " + allocation.getWorkerID());
				}
				ExecutorResult executorResult = process.getReplicaResult().getExecutorResult();
				if (executorResult != null) {
					addLine(report, executorResult.toString());
				}
			}
			addLine(report, "============================================");
		}
		
		addLine(report, "============ERRORS==========================");
		
		if (jobStatus.getState() == JobStatusInfo.FINISHED) {
			Properties prop = new Properties();
			File jobPropertiesFile = new File(
					jobsProperties.get(jobStatus.getJobId()));
			if (jobPropertiesFile.exists()) {
				try {
					prop.load(new FileInputStream(jobPropertiesFile));
					String property = prop.getProperty("output.expectedsizes");
					JsonArray expectedSizes = (JsonArray) new JsonParser().parse(property);
					if (!checkTasksOutput(jobStatus, expectedSizes, report)) {
						return false;
					}
				} catch (IOException e) {
					addLine(report, "Error while reading job properties: " + e.getMessage());
					return false;
				}
			}
			return true;
		}
		
		return false;
	}

	private void addLine(StringBuilder builder, String message) {
		builder.append(message).append("\n");
	}

	private boolean checkTasksOutput(JobStatusInfo jobStatus,
			JsonArray expectedSizes, StringBuilder builder) {
		
		String sandboxDirPath = configuration.getProperty(Conf.SANDBOX_PATH);
		
		boolean allOk = true;
		for (TaskStatusInfo taskStatus : jobStatus.getTasks()) {
			JsonArray taskExpectedSizes = (JsonArray) expectedSizes.get(taskStatus.getTaskId()-1);
			for (JsonElement expectedSizeEl : taskExpectedSizes) {
				JsonObject expectSizeObj = (JsonObject) expectedSizeEl;
				String outputName = expectSizeObj.get("name").getAsString();
				outputName = outputName.replace("$TASK", String.valueOf(taskStatus.getTaskId()))
						.replace("$JOB", String.valueOf(taskStatus.getJobId()));
				File outputFile = new File(sandboxDirPath, outputName);
				
				if (!outputFile.exists()) {
					addLine(builder, "Output " + outputName + " was not found. "
							+ "Task " + taskStatus.getId());
					allOk = false;
					continue;
				}
				
				long expectedSize = expectSizeObj.get("size").getAsLong();
				long actualSize = outputFile.length();
				
				if (actualSize != expectedSize) {
					addLine(builder, "Output " + outputName + " has an unexpected size. "
							+ "Expected: " + expectedSize + ", Actual: " + actualSize + ". "
							+ "Task " + taskStatus.getId());
					allOk = false;
					continue;
				}
			}
		}
		return allOk;
	}
	
}
