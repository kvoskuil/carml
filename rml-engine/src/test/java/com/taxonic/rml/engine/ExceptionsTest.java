package com.taxonic.rml.engine;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.function.Function;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Test;

import com.taxonic.rml.engine.template.TemplateParser;
import com.taxonic.rml.model.TriplesMap;
import com.taxonic.rml.util.IoUtils;
import com.taxonic.rml.util.RmlMappingLoader;

public class ExceptionsTest {
	private RmlMappingLoader loader = RmlMappingLoader.build();
	
	@Test(expected = Exception.class)
	public void testReadSourceException() {
		testMapping("RmlMapper/exceptionTests/exceptionReadSourceMapping.rml.ttl",
				"RmlMapper");
	}
	
	@Test(expected = Exception.class)
	public void testTermTypeExceptionA() {
		testMapping("RmlMapper/exceptionTests/exceptionTermTypeMappingA.rml.ttl",
				"RmlMapper");
	}
	
	@Test(expected = Exception.class)
	public void testTermTypeExceptionB() {
		testMapping("RmlMapper/exceptionTests/exceptionTermTypeMappingB.rml.ttl",
				"RmlMapper");
	}
	
	@Test(expected = Exception.class)
	public void testTermTypeExceptionC() {
		testMapping("RmlMapper/exceptionTests/exceptionTermTypeMappingC.rml.ttl",
				"RmlMapper");
	}
	
	@Test(expected = Exception.class)
	public void testTermTypeExceptionD() {
		testMapping("RmlMapper/exceptionTests/exceptionTermTypeMappingD.rml.ttl",
				"RmlMapper");
	}
	
	@Test(expected = Exception.class)
	public void testTermTypeExceptionE() {
		testMapping("RmlMapper/exceptionTests/exceptionTermTypeMappingE.rml.ttl",
				"RmlMapper");
	}
	

	
	private void testMapping(String rmlPath, String contextPath) {
		List<TriplesMap> mapping = loader.load(rmlPath);
		Function<String, InputStream> sourceResolver =
			s -> RmlMapperTest.class.getClassLoader().getResourceAsStream(contextPath + "/" + s);
		RmlMapper mapper = new RmlMapper(sourceResolver, TemplateParser.build());
		Model result = mapper.map(mapping);
		printModel(result);
	}
	
	private RDFFormat determineRdfFormat(String path) {
		int period = path.lastIndexOf(".");
		if (period == -1)
			return RDFFormat.TURTLE;
		String extension = path.substring(period + 1).toLowerCase();
		if (extension.equals("ttl"))
			return RDFFormat.TURTLE;
		if (extension.equals("trig"))
			return RDFFormat.TRIG;
		throw new RuntimeException(
			"could not determine rdf format from file extension [" + extension + "]");
	}

	private void printModel(Model model) {
		StringWriter writer = new StringWriter();
		Rio.write(model, writer, RDFFormat.TURTLE);
		System.out.println(writer.toString());
	}
}