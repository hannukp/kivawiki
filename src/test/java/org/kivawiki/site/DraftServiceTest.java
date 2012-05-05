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

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kivawiki.site.DraftService;
import org.kivawiki.site.DraftService.DraftResult;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DraftServiceTest {
	private ClassPathXmlApplicationContext context;
	private DraftService draftService;
	
	private static final String USER1 = "***DRAFT_TEST***";
	private static final String USER2 = "***DRAFT_TEST2***";
	
	private static final String PROJ1 = "project1";
	private static final String PROJ2 = "project2";

	@Before
	public void setupContext() {
		context = new ClassPathXmlApplicationContext("/org/kivawiki/applicationContext-testing.xml");
		draftService = context.getBean(DraftService.class);
	}
	
	@After
	public void closeContext() {
		context.destroy();
	}
	
	@Test
	public void test_basic_draft_use() throws InterruptedException {
		cleanup();

		assertEquals(0, draftService.getDrafts(USER1, PROJ1).size());
		
		// save some drafts
		draftService.saveDraft(USER1, PROJ1, "/foo.txt", "hello");
		Thread.sleep(10L);
		draftService.saveDraft(USER1, PROJ1, "/bar.txt", "world");
		Thread.sleep(10L);
		draftService.saveDraft(USER1, PROJ1, "/foo.txt", "hello");
		Thread.sleep(10L);
		draftService.saveDraft(USER1, PROJ1, "/foo.txt", "hello2");
		Thread.sleep(10L);
		
		// get the drafts, verify they are correct
		List<DraftResult> drafts = draftService.getDrafts(USER1, PROJ1);
		
		assertEquals(3, drafts.size());
		
		assertDraft("/foo.txt", "hello2", drafts.get(0));
		assertDraft("/bar.txt", "world", drafts.get(1));
		assertDraft("/foo.txt", "hello", drafts.get(2));
	}

	private void assertDraft(String expectedUri, String expectedText, DraftResult d) {
		assertEquals(expectedText, draftService.getDraft(d.username, d.proj, d.uri, d.date));
		assertEquals(expectedUri, d.uri);
	}

	private void cleanup() {
		draftService.clearDrafts(USER1, PROJ1);
		draftService.clearDrafts(USER2, PROJ1);
		draftService.clearDrafts(USER1, PROJ2);
		draftService.clearDrafts(USER2, PROJ2);
	}
}
