package org.softevo.mutation.run;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.softevo.mutation.io.Io;
import org.softevo.mutation.properties.MutationProperties;
import org.softevo.mutation.results.persistence.HibernateUtil;
import org.softevo.mutation.results.persistence.QueryManager;
import org.softevo.mutation.testsuite.RunResult;

public class ThreadPool {

	private static final int CHECK_PERIOD = 20;

	private static final String EXEC_DIR = "/scratch/schuler/mutationTest/src/scripts/";

	private static Logger logger = Logger.getLogger(ThreadPool.class);

	private static final int NUMBER_OF_THREADS = 3;

	private static final int MAX_MUTATIONS = 20000;

	private static final int NUMBER_OF_TASKS = 200;

	private static final int MUTATIONS_PER_TASK = 100;

	private static final String RESULT_DIR = MutationProperties.CONFIG_DIR
			+ "/result/";

	static {
		File resultDir = new File(RESULT_DIR);
		if (!resultDir.exists()) {
			resultDir.mkdir();
		}
	}

	// private static final String TASK_NAME = "test-no-compile";

	private static final String MUTATION_COMMAND = "/scratch/schuler/mutationTest/src/scripts/threaded-run-tests.sh";

	private static final long MAX_TIME_FOR_SUB_PROCESS = 7 * 60 * 1000;

	private final ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors
			.newFixedThreadPool(NUMBER_OF_THREADS);

	private List<Long> mutationIDs;

	private List<ProcessWrapper> processes;

	public static void main(String[] args) {
		ThreadPool tp = new ThreadPool();
		tp.startTimed();
	}

	private void startTimed() {
		long mutationResultsPre  = QueryManager.getNumberOfMutationsWithResult();
		long startTime = System.currentTimeMillis();
		logger.info("Start fetching mutations");
		mutationIDs = getMutationsIdListFromDb();
		// mutationIDs = getFakeList();
		long fetchTime = System.currentTimeMillis();
		logger.info("Fetched " + mutationIDs.size() + " mutations in "
				+ formatMilliseconds(fetchTime - startTime));
		start();
		long duration = System.currentTimeMillis() - fetchTime;
		long actuallMutationsInDb = QueryManager.getNumberOfMutationsWithResult()  - mutationResultsPre;
		logger.info("Tried to run " + MUTATIONS_PER_TASK * NUMBER_OF_TASKS
				+ " mutations - got " + actuallMutationsInDb +"  results\nRun for: " + formatMilliseconds(duration));
	}

	private String formatMilliseconds(long duration) {
		long minutes = duration / 60000;
		int seconds = (int) ((duration % 60000) / 1000);
		return minutes + "'" + seconds + "''";
	}

	private void start() {
		runTasks();
	}

	private void runTasks() {
		addProcesses();
		while (!pool.isTerminated()) {
			try {
				boolean processesFinished = pool.awaitTermination(CHECK_PERIOD,
						TimeUnit.SECONDS);
				handleProcesses(processes);
				logger.info("Processes finished:" + processesFinished);
				if (processesFinished) {
					pool.shutdown();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		handleResults(processes);
	}

	private void addProcesses() {
		List<File> files = writeTaskFiles();
		int counter = 0;
		processes = new ArrayList<ProcessWrapper>();
		for (File f : files) {
			String outputFile = String.format(RESULT_DIR
					+ "/process-output-%02d.txt", counter);

			String resultFile = String.format(RESULT_DIR
					+ "/process-result-%02d.xml", counter);
			counter++;
			ProcessWrapper ps = new ProcessWrapper(MUTATION_COMMAND,
					new String[] { "-Dmutation.file=" + f.getAbsolutePath() },
					new File(EXEC_DIR), new File(outputFile), new File(
							resultFile));
			processes.add(ps);
			logger.info("Process: " + ps.toString());
			pool.submit(ps);
		}
	}

	private void handleProcesses(List<ProcessWrapper> processes) {
		int processesFinished = 0;
		int processesRunning = 0;
		for (ProcessWrapper ps : processes) {
			if (ps.isFinished()) {
				processesFinished++;
			}
			if (ps.isRunning()) {
				processesRunning++;
				long timeRunning = ps.getTimeRunnning();
				logger.info("Process is running for "
						+ formatMilliseconds(timeRunning) + " out of "
						+ formatMilliseconds(MAX_TIME_FOR_SUB_PROCESS));
				if (timeRunning >= MAX_TIME_FOR_SUB_PROCESS) {
					logger.info("Destroying process" + ps
							+ " because time running exceeded limit: "
							+ formatMilliseconds(timeRunning));
					boolean couldBeRemoved = pool.remove(ps);
					logger.info(couldBeRemoved ? "Task was removed"
							: " Task could not be removed");
					ps.destroyProcess();
				}
			}

		}
		logger.info(processesFinished + " processes are finished and "
				+ processesRunning + " are running");
		if(processesRunning == 0 && processesFinished == NUMBER_OF_TASKS){
			pool.shutdown();
		}
		handleResults(processes);
	}

	private void handleResults(List<ProcessWrapper> processes) {
		int totalMutations = 0;
		for (ProcessWrapper ps : processes) {
			RunResult runResult = ps.getRunResult();
			if (ps.isFinished() && runResult != null) {
				totalMutations += runResult.getMutations();
			}
		}
		logger.info(totalMutations + " mutation where actually executed");
	}

	private List<File> writeTaskFiles() {
		List<File> resultFileList = new ArrayList<File>();
		for (int i = 0; i < NUMBER_OF_TASKS; i++) {
			List<Long> list = getMutionIDs(MUTATIONS_PER_TASK);
			File resultFile = writeListToFile(list, i);
			resultFileList.add(resultFile);
		}
		return resultFileList;
	}

	private File writeListToFile(List<Long> list, int id) {
		String filename = String.format(RESULT_DIR + "mutation-task-%02d.txt",
				id);
		File resultFile = new File(filename);
		StringBuilder sb = new StringBuilder();
		for (Long l : list) {
			sb.append(l);
			sb.append("\n");
		}
		Io.writeFile(sb.toString(), resultFile);
		return resultFile;
	}

	private List<Long> getFakeList() {
		List<Long> list = new ArrayList<Long>();
		for (long i = 0; i < 40000; i++) {
			list.add(i);
		}
		return list;
	}

	private List<Long> getMutionIDs(int numberOfIds) {
		List<Long> list = new ArrayList<Long>();
		Random r = new Random();
		for (int i = 0; i < numberOfIds; i++) {
			int position = r.nextInt(mutationIDs.size());
			list.add(mutationIDs.remove(position));
		}
		return list;
	}

	private static List<Long> getMutationsIdListFromDb() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createSQLQuery("SELECT m.id FROM Mutation m JOIN TestCoverageClassResult tccr ON m.classname = tccr.classname JOIN TestCoverageClassResult_TestCoverageLineResult AS class_line ON class_line.testcoverageclassresult_id = tccr.id JOIN TestCoverageLineResult AS tclr ON tclr.id = class_line.lineresults_id 	WHERE m.mutationresult_id IS NULL AND m.linenumber = tclr.linenumber");
		query.setMaxResults(MAX_MUTATIONS);
		List results = query.list();
		List<Long> idList = new ArrayList<Long>();
		for (Object id : results) {
			idList.add(Long.valueOf(id.toString()));
		}
		tx.commit();
		session.close();
		return idList;
	}
}
