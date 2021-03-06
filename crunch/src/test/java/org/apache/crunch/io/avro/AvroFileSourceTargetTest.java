/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.io.avro;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.reflect.ReflectData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.crunch.PCollection;
import org.apache.crunch.Pipeline;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.io.At;
import org.apache.crunch.io.avro.AvroFileReaderFactoryTest.PojoPerson;
import org.apache.crunch.test.Person;
import org.apache.crunch.types.avro.Avros;
import com.google.common.collect.Lists;

@SuppressWarnings("serial")
public class AvroFileSourceTargetTest implements Serializable {

	private transient File avroFile;

	@Before
	public void setUp() throws IOException {
		avroFile = File.createTempFile("test", ".avro");
	}

	@After
	public void tearDown() {
		avroFile.delete();
	}

	private void populateGenericFile(List<GenericRecord> genericRecords,
			Schema schema) throws IOException {
		FileOutputStream outputStream = new FileOutputStream(this.avroFile);
		GenericDatumWriter<GenericRecord> genericDatumWriter = new GenericDatumWriter<GenericRecord>(
				schema);

		DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<GenericRecord>(
				genericDatumWriter);
		dataFileWriter.create(schema, outputStream);

		for (GenericRecord record : genericRecords) {
			dataFileWriter.append(record);
		}

		dataFileWriter.close();
		outputStream.close();

	}

	@Test
	public void testSpecific() throws IOException {
		GenericRecord savedRecord = new GenericData.Record(Person.SCHEMA$);
		savedRecord.put("name", "John Doe");
		savedRecord.put("age", 42);
		savedRecord.put("siblingnames", Lists.newArrayList("Jimmy", "Jane"));
		populateGenericFile(Lists.newArrayList(savedRecord), Person.SCHEMA$);

		Pipeline pipeline = new MRPipeline(AvroFileSourceTargetTest.class);
		PCollection<Person> genericCollection = pipeline.read(At.avroFile(
				avroFile.getAbsolutePath(), Avros.records(Person.class)));

		List<Person> personList = Lists.newArrayList(genericCollection
				.materialize());

		Person expectedPerson = new Person();
		expectedPerson.setName("John Doe");
		expectedPerson.setAge(42);

		List<CharSequence> siblingNames = Lists.newArrayList();
		siblingNames.add("Jimmy");
		siblingNames.add("Jane");
		expectedPerson.setSiblingnames(siblingNames);

		assertEquals(Lists.newArrayList(expectedPerson),
				Lists.newArrayList(personList));
	}

	@Test
	public void testGeneric() throws IOException {
		String genericSchemaJson = Person.SCHEMA$.toString().replace("Person",
				"GenericPerson");
		Schema genericPersonSchema = new Schema.Parser()
				.parse(genericSchemaJson);
		GenericRecord savedRecord = new GenericData.Record(genericPersonSchema);
		savedRecord.put("name", "John Doe");
		savedRecord.put("age", 42);
		savedRecord.put("siblingnames", Lists.newArrayList("Jimmy", "Jane"));
		populateGenericFile(Lists.newArrayList(savedRecord),
				genericPersonSchema);

		Pipeline pipeline = new MRPipeline(AvroFileSourceTargetTest.class);
		PCollection<Record> genericCollection = pipeline
				.read(At.avroFile(avroFile.getAbsolutePath(),
						Avros.generics(genericPersonSchema)));

		List<Record> recordList = Lists.newArrayList(genericCollection
				.materialize());

		assertEquals(Lists.newArrayList(savedRecord),
				Lists.newArrayList(recordList));
	}

	@Test
	public void testReflect() throws IOException {
		Schema pojoPersonSchema = ReflectData.get().getSchema(PojoPerson.class);
		GenericRecord savedRecord = new GenericData.Record(pojoPersonSchema);
		savedRecord.put("name", "John Doe");
		populateGenericFile(Lists.newArrayList(savedRecord), pojoPersonSchema);

		Pipeline pipeline = new MRPipeline(AvroFileSourceTargetTest.class);
		PCollection<PojoPerson> personCollection = pipeline.read(At.avroFile(
				avroFile.getAbsolutePath(), Avros.reflects(PojoPerson.class)));

		List<PojoPerson> recordList = Lists.newArrayList(personCollection
				.materialize());

		assertEquals(1, recordList.size());
		PojoPerson person = recordList.get(0);
		assertEquals("John Doe", person.getName());
	}
}
