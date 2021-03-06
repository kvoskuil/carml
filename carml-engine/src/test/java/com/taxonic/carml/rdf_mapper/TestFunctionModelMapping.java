package com.taxonic.carml.rdf_mapper;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.taxonic.carml.model.TriplesMap;
import com.taxonic.carml.model.impl.LogicalSourceImpl;
import com.taxonic.carml.model.impl.ObjectMapImpl;
import com.taxonic.carml.model.impl.PredicateMapImpl;
import com.taxonic.carml.model.impl.PredicateObjectMapImpl;
import com.taxonic.carml.model.impl.TriplesMapImpl;
import com.taxonic.carml.util.RmlMappingLoader;
import com.taxonic.carml.vocab.Rdf;

public class TestFunctionModelMapping {

	private RmlMappingLoader loader = RmlMappingLoader.build();
	
	static final ValueFactory f = SimpleValueFactory.getInstance();
	
	static class Ex {
		
		static final String prefix = "http://example.com/";
		
		static IRI iri(String localName) {
			return f.createIRI(prefix, localName);
		}
		
		static final IRI
			toBoolFunction = iri("toBoolFunction"),
			isPresentBool = iri("isPresentBool");
		
	}
	
	@Test
	public void test() {

		TriplesMap functionMap =
			TriplesMapImpl.newBuilder()
				.logicalSource(
					LogicalSourceImpl.newBuilder()
						.build()
				)
				.predicateObjectMap(
					PredicateObjectMapImpl.newBuilder()
						.predicateMap(
							PredicateMapImpl.newBuilder()
								.constant(Rdf.Fno.executes)
								.build()
						)
						.objectMap(
							ObjectMapImpl.newBuilder()
								.constant(Ex.toBoolFunction)
								.build()
						)
						.build()
				)
				.build();
		
		TriplesMapImpl main =
			TriplesMapImpl.newBuilder()
				.logicalSource(
					LogicalSourceImpl.newBuilder()
						.build()
				)
				.predicateObjectMap(
					PredicateObjectMapImpl.newBuilder()
						.predicateMap(
							PredicateMapImpl.newBuilder()
								.constant(Ex.isPresentBool)
								.build()
						)
						.objectMap(
							ObjectMapImpl.newBuilder()
								.functionValue(functionMap)
								.build()
						)
						.build()
				)
				.build();
		
		Set<TriplesMap> expected = ImmutableSet.of(
			main,
			functionMap
		);

		Set<TriplesMap> result = loader.load("RmlMapper/test11/toBoolMapping2.fnml.ttl", RDFFormat.TURTLE);
		
		assertEquals(expected, result);
		
	}

}
