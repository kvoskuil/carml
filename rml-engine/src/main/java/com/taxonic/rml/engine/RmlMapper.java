package com.taxonic.rml.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.taxonic.rml.engine.function.ExecuteFunction;
import com.taxonic.rml.engine.function.Functions;
import com.taxonic.rml.model.BaseObjectMap;
import com.taxonic.rml.model.GraphMap;
import com.taxonic.rml.model.Join;
import com.taxonic.rml.model.LogicalSource;
import com.taxonic.rml.model.ObjectMap;
import com.taxonic.rml.model.PredicateObjectMap;
import com.taxonic.rml.model.RefObjectMap;
import com.taxonic.rml.model.SubjectMap;
import com.taxonic.rml.model.TriplesMap;

// TODO cache results of evaluated expressions when filling a single template, in case of repeated expressions

// TODO rr:defaultGraph

// TODO template strings should be validated during the validation step?

/* TODO re-use the ***Mapper instances for equal corresponding ***Map instances.
 * f.e. if there are 2 equal PredicateMaps in the RML mapping file,
 * re-use the same PredicateMapper instance
 */

public class RmlMapper {

	private Function<String, InputStream> sourceResolver;
	
	private static Configuration JSONPATH_CONF = Configuration.builder()
			   .options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
	
	private TermGeneratorCreator termGenerators = TermGeneratorCreator.create(this); // TODO

	private Functions functions = new Functions(); // TODO
	
	public RmlMapper(
		Function<String, InputStream> sourceResolver
	) {
		this.sourceResolver = sourceResolver;
	}

	// TODO: PM: support multiple function classes. This way it can be easily extended
	public void addFunctions(Object fn) {
		functions.addFunctions(fn);
	}
	
	public Optional<ExecuteFunction> getFunction(IRI iri) {
		return functions.getFunction(iri);
	}
	
	public Model map (List<TriplesMap> mapping) {
		return map(mapping, null);
	}

	public Model map(List<TriplesMap> mapping, InputStream input) {
		Model model = new LinkedHashModel();
		mapping.stream()
			.filter(m -> !isTriplesMapOnlyUsedAsFunctionValue(m, mapping))
			.forEach(m ->map(m, model, input));
		return model;
	}
	
	private boolean isTriplesMapOnlyUsedAsFunctionValue(TriplesMap map, List<TriplesMap> mapping) {
		return
			isTriplesMapUsedAsFunctionValue(map, mapping) &&
			!isTriplesMapUsedInRefObjectMap(map, mapping);
	}
	
	private boolean isTriplesMapUsedAsFunctionValue(TriplesMap map, List<TriplesMap> mapping) {
		
		// TODO
		
		return false;
	}
	
	private boolean isTriplesMapUsedInRefObjectMap(TriplesMap map, List<TriplesMap> mapping) {
		return
		mapping.stream()
		
			// get all referencing object maps
			.flatMap(m -> m.getPredicateObjectMaps().stream())
			.flatMap(p -> p.getObjectMaps().stream())
			.filter(o -> o instanceof RefObjectMap)
			.map(o -> (RefObjectMap) o)
			
			// check that no referencing object map
			// has 'map' as its parent triples map
			.map(o -> o.getParentTriplesMap())
			.anyMatch(map::equals);
		
	}
	
	private void map(TriplesMap triplesMap, Model model, InputStream input) {
		TriplesMapper triplesMapper = createTriplesMapper(triplesMap, input); // TODO cache mapper instances
		triplesMapper.map(model);
	}
	
	public String readSource(String source) {
		try (Reader reader = new InputStreamReader(
			sourceResolver.apply(source),
			StandardCharsets.UTF_8
		)) {
			// TODO depending on transitive dependency here, because newer commons-io resulted in conflict with version used by rdf4j
			return IOUtils.toString(reader);
		}
		catch (IOException e) {
			throw new RuntimeException("error reading source [" + source + "]", e);
		}
	}
	
	private List<TermGenerator<IRI>> createGraphGenerators(Set<GraphMap> graphMaps) {
		return graphMaps.stream()
			.map(termGenerators::getGraphGenerator)
			.collect(Collectors.toList());
	}
	
	
	
	
	
	
	private Stream<TermGenerator<Value>> getObjectMapGenerators(
		Set<BaseObjectMap> objectMaps
	) {
		return objectMaps.stream()
			.filter(o -> o instanceof ObjectMap)
			.map(o -> termGenerators.getObjectGenerator((ObjectMap) o));
	}

	private RefObjectMap checkLogicalSource(RefObjectMap o, LogicalSource logicalSource) {
		LogicalSource parentLogicalSource = o.getParentTriplesMap().getLogicalSource();
		if (!logicalSource.equals(parentLogicalSource))
			throw new RuntimeException(
				"Logical sources are not equal.\n" +
				"Parent: " + parentLogicalSource + "\n" +
				"Child: " + logicalSource
			);
		return o;
	}
	
	private Stream<TermGenerator<Value>> getJoinlessRefObjectMapGenerators(
		Set<BaseObjectMap> objectMaps, LogicalSource logicalSource
	) {
		return objectMaps.stream()
			.filter(o -> o instanceof RefObjectMap)
			.map(o -> (RefObjectMap) o)
			.filter(o -> o.getJoinConditions().isEmpty())
			.map(o -> checkLogicalSource(o, logicalSource))
			.map(o -> (TermGenerator<Value>) (Object) // TODO not very nice
				createRefObjectJoinlessMapper(o));
	}
	
	private TermGenerator<Resource> createRefObjectJoinlessMapper(RefObjectMap refObjectMap) {
		return termGenerators.getSubjectGenerator(
			refObjectMap.getParentTriplesMap().getSubjectMap()
		);
	}
	
	private List<PredicateObjectMapper> createPredicateObjectMappers(TriplesMap triplesMap, Set<PredicateObjectMap> predicateObjectMaps) {
		return predicateObjectMaps.stream().map(m -> {
			
			Set<BaseObjectMap> objectMaps = m.getObjectMaps();
			
			List<PredicateMapper> predicateMappers =
				m.getPredicateMaps().stream().map(p -> {

					List<TermGenerator<Value>> objectGenerators =
						Stream.concat(
						
							// object maps -> object generators
							getObjectMapGenerators(objectMaps),
							
							// ref object maps without joins -> object generators.
							// ref object maps without joins MUST have an identical logical source.
							getJoinlessRefObjectMapGenerators(objectMaps, triplesMap.getLogicalSource())
							
						)
						.collect(Collectors.toList());
					
					List<RefObjectMapper> refObjectMappers =
						objectMaps.stream()
							.filter(o -> o instanceof RefObjectMap)
							.map(o -> (RefObjectMap) o)
							.filter(o -> !o.getJoinConditions().isEmpty())
							.map(o -> createRefObjectMapper(o))
							.collect(Collectors.toList());

					return new PredicateMapper(
						termGenerators.getPredicateGenerator(p),
						objectGenerators,
						refObjectMappers
					);
				})
				.collect(Collectors.toList());
			
			return new PredicateObjectMapper(
				createGraphGenerators(m.getGraphMaps()),
				predicateMappers
			);
		})
		.collect(Collectors.toList());
	}
	
	SubjectMapper createSubjectMapper(TriplesMap triplesMap) {
		// TODO: NullPointerException. Can we predict and avoid?
		SubjectMap subjectMap = triplesMap.getSubjectMap();
		return
		new SubjectMapper(
			termGenerators.getSubjectGenerator(subjectMap),
			createGraphGenerators(subjectMap.getGraphMaps()),
			subjectMap.getClasses(),
			createPredicateObjectMappers(triplesMap, triplesMap.getPredicateObjectMaps())
		);
	}
	
	private TriplesMapper createTriplesMapper(TriplesMap triplesMap, InputStream input) {
		
		LogicalSource logicalSource = triplesMap.getLogicalSource();
		
//		logicalSource.getReferenceFormulation();
		
		// TODO this all assumes json
		
		InputStream source = 
				logicalSource.getSource().equals("http://carml.org/InputStream") ? 
						input : 
						sourceResolver.apply(logicalSource.getSource());
		
		String iterator = logicalSource.getIterator();
		Function<InputStream, Object> applyIterator =
			s -> {
				try {
					Object iteration = JsonPath.using(JSONPATH_CONF).parse(s).read(iterator);
					// Reset for next triples map
					s.reset();
					return iteration;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return s;
			};
			
		Function<Object, EvaluateExpression> expressionEvaluatorFactory =
			object -> expression -> JsonPath.using(JSONPATH_CONF).parse(object).read(expression);
		
		return
		new TriplesMapper(
			source,
			applyIterator,
			expressionEvaluatorFactory,
			createSubjectMapper(triplesMap)
		);
	}
	
	private ParentTriplesMapper createParentTriplesMapper(TriplesMap triplesMap) {
		
		LogicalSource logicalSource = triplesMap.getLogicalSource();
		
//		logicalSource.getReferenceFormulation();
		
		// TODO this all assumes json
		Supplier<Object> getSource = () -> readSource(logicalSource.getSource());
		
		String iterator = logicalSource.getIterator();
		UnaryOperator<Object> applyIterator =
			s -> JsonPath.using(JSONPATH_CONF).parse(s).read(iterator);
			
		Function<Object, EvaluateExpression> expressionEvaluatorFactory =
			object -> expression -> JsonPath.using(JSONPATH_CONF).parse(object).read(expression);
		
		return
		new ParentTriplesMapper(
			termGenerators.getSubjectGenerator(triplesMap.getSubjectMap()), 
			getSource, 
			applyIterator, 
			expressionEvaluatorFactory
		);
	}

	private RefObjectMapper createRefObjectMapper(RefObjectMap refObjectMap) {
		Set<Join> joinConditions = refObjectMap.getJoinConditions();
		return new RefObjectMapper(
			createParentTriplesMapper(refObjectMap.getParentTriplesMap()),
			joinConditions
		);
	};
	
}
