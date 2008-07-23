package org.softevo.mutation.javaagent.classFileTransfomer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.softevo.mutation.bytecodeMutations.MutationTransformer;
import org.softevo.mutation.bytecodeMutations.integrateSuite.IntegrateSuiteTransformer;
import org.softevo.mutation.bytecodeMutations.removeSystemExit.RemoveSystemExitTransformer;
import org.softevo.mutation.io.XmlIo;
import org.softevo.mutation.javaagent.MutationForRun;
import org.softevo.mutation.mutationPossibilities.MutationPossibilityCollector;
import org.softevo.mutation.objectInspector.asmAdapters.ObjectInspectorTransformer;
import org.softevo.mutation.properties.MutationProperties;
import org.softevo.mutation.results.Mutation;
import org.softevo.mutation.results.Mutation.MutationType;
import org.softevo.mutation.results.persistence.QueryManager;

import de.unisb.st.bytecodetransformer.processFiles.BytecodeTransformer;

/**
 * {@link MutationTransformer} is used to apply mutations during runtime via a
 * java agent.
 *
 * @author David Schuler
 *
 */
@SuppressWarnings("unchecked")
public class MutationFileTransformer implements ClassFileTransformer {


	private static Logger logger = Logger
			.getLogger(MutationFileTransformer.class);

	static {
		// DB must be loaded before transform method is entered. Otherwise
		// program crashes.
		Mutation someMutation = new Mutation("SomeMutationToAddToTheDb", 23,
				23, MutationType.ARITHMETIC_REPLACE,false);
		Mutation mutationFromDb = QueryManager.getMutationOrNull(someMutation);
		if (mutationFromDb == null) {
			MutationPossibilityCollector mpc1 = new MutationPossibilityCollector();
			mpc1.addPossibility(someMutation);
			mpc1.toDB();
		}

	}

	private static String testName = ".testclasses";

	private static MutationTransformer mutationTransformer = new MutationTransformer();

	private static MutationForRun mm = MutationForRun.getInstance();

	private static Collection<String> classesToMutate = mm.getClassNames();

	private static RemoveSystemExitTransformer systemExitTransformer = new RemoveSystemExitTransformer();

	private static final String[] systemExitClasses = new String[] {
			"org.aspectj.tools.ajbrowser.ui.swing.TopFrame",
			"org.aspectj.tools.ajdoc.JavadocRunner$1",
			"org.aspectj.tools.ajdoc.Main",
			"org.aspectj.apache.bcel.classfile.Utility",
			"org.aspectj.apache.bcel.util.CodeHTML",
			"org.aspectj.apache.bcel.verifier.NativeVerifier",
			"org.aspectj.apache.bcel.verifier.TransitiveHull",
			"org.aspectj.apache.bcel.verifier.VerifierAppFrame",
			"org.aspectj.apache.bcel.verifier.VerifyDialog$1",
			"$installer$.org.aspectj.Main",
			"org.aspectj.tools.ajc.Main",
			"RunWeaveTests",
			"WeaveTests",
			"org.aspectj.internal.tools.ant.taskdefs.MainWrapper$1",
			"org.aspectj.internal.tools.ant.taskdefs.MainWrapper",
			"org.aspectj.testing.util.LinkCheck",
			"org.aspectj.testing.server.TestServer",
			"org.aspectj.testing.drivers.Harness",
			"org.softevo.mutation.bytecodeMutations.sysexit.testclasses.SysExit", };

	private static List<String> systemExitClassList = Arrays
			.asList(systemExitClasses);

//	private static Set<String> testCases = (Set<String>) XmlIo
//			.fromXml(MutationProperties.TESTCASES_FILE);

	static{
		logger.info("Loading MutationFileTransformer");
		logger.info("Log4J Configuration at: "
				+ System.getProperty("log4j.configuration"));
//		logger.info("TestCases");
//		logger.info(testCases);
	}

	private static MutationDecision mutationDecision = new MutationDecision() {

		public boolean shouldBeHandled(String classNameWithDots) {
			if (classesToMutate.contains(classNameWithDots)) {
				return true;
			}
			if (classNameWithDots.contains(testName)
					&& !classNameWithDots.endsWith("Test")) {
				// Hack for unittesting
				return true;
			}
			return false;
		}

	};

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.instrument.ClassFileTransformer#transform(java.lang.ClassLoader,
	 *      java.lang.String, java.lang.Class, java.security.ProtectionDomain,
	 *      byte[])
	 */
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			String classNameWithDots = className.replace('/', '.');
			// logger.info(className + " is passed to transformer");
			if (isSystemExitClass(classNameWithDots)) {
				logger.info("Trying to remove calls to system exit from class"
						+ classNameWithDots);
				classfileBuffer = systemExitTransformer
						.transformBytecode(classfileBuffer);
			}
			if (compareWithSuiteProperty(classNameWithDots) || classNameWithDots.endsWith("AllTests")) {
				logger.info("Trying to integrate SelectiveTestSuite");
				BytecodeTransformer integrateSuiteTransformer =  IntegrateSuiteTransformer.getIntegrateSelectiveTestSuiteTransformer();
				classfileBuffer = integrateSuiteTransformer
						.transformBytecode(classfileBuffer);
			}
			if (isObservedTestCase(classNameWithDots)) {
				try {
					logger.info("Trying to transform test class "
							+ classNameWithDots);
					ObjectInspectorTransformer objectInspectorTransformer = new ObjectInspectorTransformer();
					classfileBuffer = objectInspectorTransformer
							.transformBytecode(classfileBuffer);
				} catch (Exception e) {
					logger.warn("Exception Thrown" + e);
					e.printStackTrace();
				}
				logger.info("Test class transformed " + classNameWithDots);
			}

			if (mutationDecision.shouldBeHandled(classNameWithDots)) {
				logger.info("Transforming: " + classNameWithDots);
				byte[] transformedBytecode = null;
				try {
					transformedBytecode = mutationTransformer
							.transformBytecode(classfileBuffer);
				} catch (Exception e) {
					logger.info("Exception thrown: " + e);
					e.printStackTrace();
				}
				logger.info("Class transformed: " + classNameWithDots);
				return transformedBytecode;
			}
		} catch (Exception e) {
			logger.fatal(e.getMessage());
			e.printStackTrace();
			System.exit(0);
//			throw new RuntimeException(e.getMessage());
		}
		return classfileBuffer;
	}

	/**
	 * Decides if the objects that exist in this test case should be serialized.
	 * This is configured in an xml file (
	 *
	 * @see MutationProperties.TESTCASES_FILE).
	 *
	 * @param classNameWithDots
	 *            The name of the class seperated with dots
	 * @return True, if the objects should be serialized.
	 */
	private boolean isObservedTestCase(String classNameWithDots) {
		if (MutationProperties.OBSERVE_OBJECTS) {
//			if (testCases.contains(classNameWithDots)) {
//				return true;
//			} else
			if (classNameWithDots.endsWith("InspectorTest")) {
				return true;
			}
		}
		return false;
	}

	public static boolean compareWithSuiteProperty(String classNameWithDots) {
		boolean returnValue = false;
		String testSuiteName = System
				.getProperty(MutationProperties.TEST_SUITE_KEY);
		if (testSuiteName != null && classNameWithDots.contains(testSuiteName)) {
			returnValue = true;
		}
		return returnValue;
	}

	private boolean isSystemExitClass(String classNameWithDots) {
		if (classNameWithDots.toLowerCase().contains("Main")) {
			logger.info("Checking " + classNameWithDots + " for System exit");
		}
		return systemExitClassList.contains(classNameWithDots);
	}

}