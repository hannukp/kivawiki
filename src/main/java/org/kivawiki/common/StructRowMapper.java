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

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;

import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Maps from a ResultSet to Struct's fields.
 */
public class StructRowMapper<T> implements RowMapper<T> {
	private final Class<T> mappedClass;
	private final HashMap<String, Field> mappedFields;
	
	public StructRowMapper(Class<T> mappedClass) {
		this.mappedClass = mappedClass;
		this.mappedFields = new HashMap<String, Field>();
		for (Field f : mappedClass.getFields()) {
			this.mappedFields.put(f.getName().toLowerCase(), f);
		}
	}

	public static <T> StructRowMapper<T> of(Class<T> mappedClass) {
		return new StructRowMapper<T>(mappedClass);
	}
	
	@Override
	public T mapRow(ResultSet rs, int rowNum) throws SQLException {
		T mappedObject = BeanUtils.instantiate(this.mappedClass);
		ResultSetMetaData rsmd = rs.getMetaData();
		int columnCount = rsmd.getColumnCount();
		for (int index = 1; index <= columnCount; index++) {
			String column = JdbcUtils.lookupColumnName(rsmd, index);
			column = column.toLowerCase().replace(" ", "").replace("_", "");
			Field field = mappedFields.get(column);
			if (field != null) {
				Object value = JdbcUtils.getResultSetValue(rs, index, field.getType());
				ReflectionUtils.setField(field, mappedObject, value);
			}
		}
		return mappedObject;
	}
}
