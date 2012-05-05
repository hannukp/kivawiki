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
package org.kivawiki.controllers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.kivawiki.controllers.EditController;

public class EditControllerTest {
	@Test
	public void verify_mergeDocuments() {
		assertEquals("a\nxyxy\nd\n", EditController.mergeDocuments("a\nb\nc\nd\n", "xyxy", 2, 4));
		assertEquals("xyxy\nc\nd\n", EditController.mergeDocuments("a\nb\nc\nd\n", "xyxy", null, 3));
		assertEquals("a\nb\nxyxy", EditController.mergeDocuments("a\nb\nc\nd\n", "xyxy", 3, null));
		assertEquals("xyxy", EditController.mergeDocuments("a\nb\nc\nd\n", "xyxy", null, null));
	}
	
	@Test
	public void verify_extractFragment() {
		assertEquals("b\nc", EditController.extractFragment("a\nb\nc\nd\n", 2, 4));
		assertEquals("b\nc\nd", EditController.extractFragment("a\nb\nc\nd\n", 2, 5));
		assertEquals("b\nc\nd\n", EditController.extractFragment("a\nb\nc\nd\n", 2, 6));
		assertEquals("a\nb\nc\nd\n", EditController.extractFragment("a\nb\nc\nd\n", null, null));
		assertEquals("a\nb", EditController.extractFragment("a\nb\nc\nd\n", null, 3));
		assertEquals("c\nd\n", EditController.extractFragment("a\nb\nc\nd\n", 3, null));
	}
	
	@Test
	public void merging_extracted_fragment_should_yield_original() {
		String document = "a\nb\nc\nd\n";
		for (int i = 1; i < 6; i++) {
			for (int e = i + 1; e < 6; e++) {
				String extracted = EditController.extractFragment(document, i, e);
				String merged = EditController.mergeDocuments(document, extracted, i, e);
				assertEquals(document, merged);
			}
		}
	}
}
