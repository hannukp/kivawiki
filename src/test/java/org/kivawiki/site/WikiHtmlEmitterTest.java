/**
 * Copyright 2012 Hannu Kankaanpää
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 * @author Hannu Kankaanpää <hannu.kp@gmail.com>
 */
package org.kivawiki.site;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.kivadoc.FullDocumentParser;
import org.kivadoc.KivaDocument;
import org.kivadoc.emitters.FullHtmlEmitter;
import org.kivadoc.emitters.DocHtmlEmitter.DocTarget;
import org.kivawiki.site.WikiHtmlEmitter;
import org.kivawiki.site.WikiService;

public class WikiHtmlEmitterTest {
	private WikiService wiki = mock(WikiService.class);
	private String viewContextPath = "http://example.com/view/prj1";
	private String editContextPath = "http://example.com/edit/prj1";
	private String proj = "test";
	private String documentUri = "/nana/foo.txt";
	private WikiHtmlEmitter wikiHtmlEmitter;
	
	@Before
	public void setup() {
		wikiHtmlEmitter = new WikiHtmlEmitter(wiki, viewContextPath, editContextPath, proj, documentUri);
	}
	
	@Test
	public void verify_edit_sections_work() throws IOException {
		FullDocumentParser parser = new FullDocumentParser();
		KivaDocument doc = parser.parse(resourceToString("header_test.txt"));
		
		FullHtmlEmitter emitter = new FullHtmlEmitter();
		emitter.setDocHtmlEmitter(wikiHtmlEmitter);
		String actualHtml = emitter.toHtmlString(doc);
		assertEquals(0, doc.getErrors().size());
		String expectedHtml = resourceToString("header_test.html");
		if (!normalizeHtml(expectedHtml).equals(normalizeHtml(actualHtml))) {
			System.err.println("====================================");
			System.err.println(actualHtml);
			System.err.println("====================================");
			fail();
		}
	}
	
	@Test
	public void test_getTitle_basic() {
		DocTarget target = new DocTarget();
		target.uri = "/yes";
		target.frag = null;
		when(wiki.fileUri("/yes.html")).thenReturn("/yes.txt");
		when(wiki.doesResourceExist(proj, "/yes.txt")).thenReturn(true);
		when(wiki.getTitle(proj, "/yes.html")).thenReturn("ok");
		assertEquals("ok", wikiHtmlEmitter.getTitle(target));
		assertEquals(0, wikiHtmlEmitter.getErrors().size());
	}
	
	@Test
	public void test_getTitle_fragment() {
		DocTarget target = new DocTarget();
		target.uri = "/yes2";
		target.frag = "frag";
		when(wiki.fileUri("/yes2.html")).thenReturn("/yes2.txt");
		when(wiki.doesResourceExist(proj, "/yes2.txt")).thenReturn(true);
		when(wiki.getTitle(proj, "/yes2.html")).thenReturn("ok2");
		assertEquals("ok2 » frag", wikiHtmlEmitter.getTitle(target));
		assertEquals(0, wikiHtmlEmitter.getErrors().size());
	}
	
	@Test
	public void test_resourceExists() {
		when(wiki.doesResourceExist(proj, "/bar.png")).thenReturn(false);
		
		assertFalse(wikiHtmlEmitter.doesResourceExist("/bar.png"));
		
		when(wiki.doesResourceExist(proj, "/foo.png")).thenReturn(true);
		
		assertTrue(wikiHtmlEmitter.doesResourceExist("/foo.png"));
	}
	
	private String resourceToString(String resourceName) throws IOException {
		return IOUtils.toString(WikiHtmlEmitterTest.class.getResourceAsStream("/org/kivawiki/" + resourceName), "UTF-8");
	}
	
	private String normalizeHtml(String html) {
		return html.trim().replace("\r", "").replaceAll(">\\s*<", "><").replace("<p> ", "<p>").replace(" </p>", "</p>").replace("\n</p>", "</p>").replace("<p>\n", "<p>");
	}
}
