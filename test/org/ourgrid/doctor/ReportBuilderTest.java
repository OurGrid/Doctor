package org.ourgrid.doctor;

import junit.framework.Assert;

import org.junit.Test;

public class ReportBuilderTest {

	@Test
	public void testLoad() {
		ReportBuilder builder = new ReportBuilder();
		String report = builder.load("<html></html>").build();
		Assert.assertEquals("<html></html>", report);
	}
	
	@Test
	public void testSet() {
		ReportBuilder builder = new ReportBuilder();
		String report = builder.load("<html>%X%</html>")
				.set("X", "Hello")
				.build();
		Assert.assertEquals("<html>Hello</html>", report);
	}
	
	@Test
	public void testAdd() {
		ReportBuilder builder = new ReportBuilder();
		String report = builder.load("<html>%X%</html>")
				.add("X", "Hello")
				.add("X", "Hi")
				.add("X", "How are you")
				.build();
		Assert.assertEquals("<html>HelloHiHow are you</html>", report);
	}
}
