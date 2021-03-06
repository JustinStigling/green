package za.ac.sun.cs.green.service.factorizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apfloat.Apint;

import za.ac.sun.cs.green.Green;
import za.ac.sun.cs.green.Instance;
import za.ac.sun.cs.green.Service;
import za.ac.sun.cs.green.expr.Expression;
import za.ac.sun.cs.green.service.BasicService;
import za.ac.sun.cs.green.util.Reporter;

public class CountFactorizerService extends BasicService {

	private static final String FACTORS = "FACTORS";
	private static final String COUNT = "COUNT";

	private static final String FACTORS_UNSOLVED = "FACTORS_UNSOLVED";
	
	/**
	 * Number of times the slicer has been invoked.
	 */
	private int invocationCount = 0;

	/**
	 * Total number of constraints processed.
	 */
	private int constraintCount = 0;

	/**
	 * Number of factored constraints returned.
	 */
	private int factorCount = 0;

	public CountFactorizerService(Green solver) {
		super(solver);
	}

	@Override
	public Set<Instance> processRequest(Instance instance) {
		invocationCount++;
		@SuppressWarnings("unchecked")
		Set<Instance> result = (Set<Instance>) instance.getData(FACTORS);
		if (result == null) {
			final Instance p = instance.getParent();

			FactorExpression fc0 = null;
			if (p != null) {
				fc0 = (FactorExpression) p.getData(FactorExpression.class);
				if (fc0 == null) {
					// Construct the parent's factor and store it
					fc0 = new FactorExpression(null, p.getFullExpression());
					p.setData(FactorExpression.class, fc0);
				}
			}

			final FactorExpression fc = new FactorExpression(fc0, instance.getExpression());
			instance.setData(FactorExpression.class, fc);

			result = new HashSet<Instance>();
			for (Expression e : fc.getFactors()) {
				log.info("Factorizer computes instance for :" + e);
				final Instance i = new Instance(getSolver(), instance.getSource(), null, e);
				result.add(i);
			}
			result = Collections.unmodifiableSet(result);
			instance.setData(FACTORS, result);
			instance.setData(COUNT, Apint.ONE);
			instance.setData(FACTORS_UNSOLVED, new HashSet<Instance>(result));

			log.info("Factorize exiting with " + result.size() + " results");

			constraintCount += 1;
			factorCount += fc.getNumFactors();
		}
		return result;
	}

	@Override
	public Object childDone(Instance instance, Service subservice, Instance subinstance, Object result) {
		Apint count = (Apint) result;
		if ((count != null) && count.equals(Apint.ZERO)) {
			log.warn("Count for a FACTOR is ZERO, this should not happen!");
			return Apint.ZERO;
		}
		@SuppressWarnings("unchecked")
		HashSet<Instance> unsolved = (HashSet<Instance>) instance.getData(FACTORS_UNSOLVED);
		if (unsolved.contains(subinstance)) {
			Apint total = (Apint)instance.getData(COUNT);
			total = total.multiply(count);
			instance.setData(COUNT, total);
			// Remove the subinstance now that it is solved 
			unsolved.remove(subinstance);
			instance.setData(FACTORS_UNSOLVED, unsolved);
			// Return true if no more unsolved factors; else return null to carry on the computation
			return (unsolved.isEmpty()) ? total : null; 
		} else {
			// We have already solved this subinstance; return null to carry on the computation
			return null;
		}
	}
	

	@Override
	public Object allChildrenDone(Instance instance, Object result) {
		@SuppressWarnings("unchecked")
		HashSet<Instance> unsolved = (HashSet<Instance>) instance.getData(FACTORS_UNSOLVED);
		if (unsolved.size() >= 1 && result == null) {
			log.fatal("Unsolved Factors but result is null -> concurrency bug");
			result = true;
		}
		return result;
	}
	
	@Override
	public void report(Reporter reporter) {
		reporter.report(getClass().getSimpleName(), "invocations = " + invocationCount);
		reporter.report(getClass().getSimpleName(), "totalConstraints = " + constraintCount);
		reporter.report(getClass().getSimpleName(), "factoredConstraints = " + factorCount);
	}

}
