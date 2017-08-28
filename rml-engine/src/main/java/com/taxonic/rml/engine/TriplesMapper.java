package com.taxonic.rml.engine;

import java.io.InputStream;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.eclipse.rdf4j.model.Model;

class TriplesMapper {
	
	private InputStream source;
	private Function<InputStream, Object> applyIterator;
	private Function<Object, EvaluateExpression> expressionEvaluatorFactory;
	private SubjectMapper subjectMapper;
	
	TriplesMapper(
		InputStream source,
		Function<InputStream, Object> applyIterator,
		Function<Object, EvaluateExpression> expressionEvaluatorFactory,
		SubjectMapper subjectMapper
	) {
		this.source = source;
		this.applyIterator = applyIterator;
		this.expressionEvaluatorFactory = expressionEvaluatorFactory;
		this.subjectMapper = subjectMapper;
	}

	private Iterable<?> createIterable(Object value) {
		if (value == null)
			return Collections.emptyList();
		boolean isIterable = value instanceof Iterable;
		return isIterable
			? (Iterable<?>) value
			: Collections.singleton(value);
	}
	
	void map(Model model) {
		Object value = applyIterator.apply(source);
		Iterable<?> iterable = createIterable(value);
		iterable.forEach(e -> map(e, model));
	}
	
	private void map(Object entry, Model model) {
		EvaluateExpression evaluate =
			expressionEvaluatorFactory.apply(entry);
		subjectMapper.map(model, evaluate);
	}
}
