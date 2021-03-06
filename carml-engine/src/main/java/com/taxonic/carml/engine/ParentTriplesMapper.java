package com.taxonic.carml.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.eclipse.rdf4j.model.Resource;

class ParentTriplesMapper {
	
	private TermGenerator<Resource> subjectGenerator;
	private Supplier<Object> getSource;
	private UnaryOperator<Object> applyIterator;
	private Function<Object, EvaluateExpression> expressionEvaluatorFactory;
	
	ParentTriplesMapper(
		TermGenerator<Resource> subjectGenerator,
		Supplier<Object> getSource,
		UnaryOperator<Object> applyIterator,
		Function<Object, EvaluateExpression> expressionEvaluatorFactory
	) {
		this.subjectGenerator = subjectGenerator;
		this.getSource = getSource;
		this.applyIterator = applyIterator;
		this.expressionEvaluatorFactory = expressionEvaluatorFactory;
	}

	private Iterable<?> createIterable(Object value) {
		boolean isIterable = Iterable.class.isAssignableFrom(value.getClass());
		return isIterable
			? (Iterable<?>) value
			: Collections.singleton(value);
	}
	
	Set<Resource> map(Map<String, Object> joinValues) {
		Object source = getSource.get();
		Object value = applyIterator.apply(source);
		Iterable<?> iterable = createIterable(value);
		Set<Resource> results = new LinkedHashSet<>();
		iterable.forEach(e -> 
			map(e, joinValues)
				.ifPresent(results::add));
		return results;
	}
	
	private Optional<Resource> map(Object entry, Map<String, Object> joinValues) {
		// example of joinValues: key: "$.country.name", value: "Belgium"
		EvaluateExpression evaluate =
			expressionEvaluatorFactory.apply(entry);
		boolean isValidJoin = joinValues.keySet().stream().allMatch(parentExpression -> {
			Optional<Object> parentValue = evaluate.apply(parentExpression);
			Object requiredValue = joinValues.get(parentExpression);
			return parentValue
					.map(p -> Objects.equals(p, requiredValue))
					.orElse(false);
		});
		 if (isValidJoin) {
			 return subjectGenerator.apply(evaluate);
		 }
		 return Optional.empty();
		
	}
}
