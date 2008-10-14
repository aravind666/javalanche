package de.st.cs.unisb.javalanche.hibernate;

import java.util.HashSet;
import java.util.List;

import junit.framework.TestResult;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import de.st.cs.unisb.javalanche.results.Mutation;
import de.st.cs.unisb.javalanche.results.MutationTestResult;
import de.st.cs.unisb.javalanche.results.Mutation.MutationType;
import de.st.cs.unisb.javalanche.results.persistence.HibernateUtil;
import de.st.cs.unisb.javalanche.runtime.MutationTestListener;

@SuppressWarnings("unchecked")
// Because of lists returned by hibernate
public class HibernateTest {

	private static Mutation testMutaion = new Mutation("testClass", 21, 0,
			MutationType.RIC_PLUS_1, false);

	@BeforeClass
	public static void hibernateSave() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		TestResult tr= new TestResult();
		testMutaion.setMutationResult(new MutationTestResult(tr,new MutationTestListener(), new HashSet<String>()));
		session.save(testMutaion);
		tx.commit();
		session.close();
	}

	@AfterClass
	public static void hibernateDelete() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createQuery("from Mutation where className=:name");
		query.setString("name", testMutaion.getClassName());
		List l = query.list();
		for (Object object : l) {
			session.delete(object);
		}
		tx.commit();
		session.close();
	}

	@Test
	public void hibernateQueryByLine() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session.createQuery("from Mutation where lineNumber="
				+ testMutaion.getLineNumber());
		query.setMaxResults(20);
		List results = query.list();
		int count = 0;
		for (Object o : results) {
			Assert.assertTrue(o instanceof Mutation);
			count++;

		}
		Assert.assertTrue("Expected at least one mutation for line"
				+ testMutaion.getLineNumber(), count > 0);
		tx.commit();
		session.close();
	}

	@Test(timeout = 5000)
	public void hibernateQueryByType() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session.createQuery("from Mutation where mutationtype="
				+ testMutaion.getMutationType().ordinal());
		query.setMaxResults(100);
		List results = query.list();
		for (Object o : results) {
			if (o instanceof Mutation) {
			} else {
				throw new RuntimeException("Expected other Type. Was: "
						+ o.getClass() + " Expected: " + Mutation.class);
			}
		}
		Assert.assertTrue("expected at least one result for mutationtype "
				+ testMutaion.getMutationType().toString(), results.size() > 0);
		tx.commit();
		session.close();
	}

}