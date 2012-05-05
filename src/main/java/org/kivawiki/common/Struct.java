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
package org.kivawiki.common;

import java.io.Serializable;
import java.lang.reflect.Field;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.springframework.util.ReflectionUtils;

/**
 * Struct is a simple collection of public fields.
 */
public abstract class Struct implements Serializable {
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this);
	}
	
	public Object get(String key) {
		Field field = ReflectionUtils.findField(getClass(), key);
		if (field == null) {
			throw new IllegalArgumentException("Key '" + key + "' not found in object " + this);
		}
		return ReflectionUtils.getField(field, this);
	}
}
