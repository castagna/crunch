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
package org.apache.crunch.lib.join;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.apache.crunch.FilterFn;
import org.apache.crunch.MapFn;
import org.apache.crunch.PTable;
import org.apache.crunch.Pair;
import org.apache.crunch.Pipeline;
import org.apache.crunch.impl.mem.MemPipeline;
import org.apache.crunch.impl.mr.MRPipeline;
import org.apache.crunch.impl.mr.run.CrunchRuntimeException;
import org.apache.crunch.test.FileHelper;
import org.apache.crunch.types.writable.Writables;
import com.google.common.collect.Lists;

public class MapsideJoinTest {

  private static class LineSplitter extends MapFn<String, Pair<Integer, String>> {

    @Override
    public Pair<Integer, String> map(String input) {
      String[] fields = input.split("\\|");
      return Pair.of(Integer.parseInt(fields[0]), fields[1]);
    }

  }

  private static class NegativeFilter extends FilterFn<Pair<Integer, String>> {

    @Override
    public boolean accept(Pair<Integer, String> input) {
      return false;
    }

  }

  @Test(expected = CrunchRuntimeException.class)
  public void testNonMapReducePipeline() {
    runMapsideJoin(MemPipeline.getInstance());
  }

  @Test
  public void testMapsideJoin_RightSideIsEmpty() throws IOException {
    MRPipeline pipeline = new MRPipeline(MapsideJoinTest.class);
    PTable<Integer, String> customerTable = readTable(pipeline, "customers.txt");
    PTable<Integer, String> orderTable = readTable(pipeline, "orders.txt");

    PTable<Integer, String> filteredOrderTable = orderTable.parallelDo(new NegativeFilter(),
        orderTable.getPTableType());

    PTable<Integer, Pair<String, String>> joined = MapsideJoin.join(customerTable,
        filteredOrderTable);

    List<Pair<Integer, Pair<String, String>>> materializedJoin = Lists.newArrayList(joined
        .materialize());

    assertTrue(materializedJoin.isEmpty());

  }

  @Test
  public void testMapsideJoin() throws IOException {
    runMapsideJoin(new MRPipeline(MapsideJoinTest.class));
  }

  private void runMapsideJoin(Pipeline pipeline) {
    PTable<Integer, String> customerTable = readTable(pipeline, "customers.txt");
    PTable<Integer, String> orderTable = readTable(pipeline, "orders.txt");

    PTable<Integer, Pair<String, String>> joined = MapsideJoin.join(customerTable, orderTable);

    List<Pair<Integer, Pair<String, String>>> expectedJoinResult = Lists.newArrayList();
    expectedJoinResult.add(Pair.of(111, Pair.of("John Doe", "Corn flakes")));
    expectedJoinResult.add(Pair.of(222, Pair.of("Jane Doe", "Toilet paper")));
    expectedJoinResult.add(Pair.of(222, Pair.of("Jane Doe", "Toilet plunger")));
    expectedJoinResult.add(Pair.of(333, Pair.of("Someone Else", "Toilet brush")));

    List<Pair<Integer, Pair<String, String>>> joinedResultList = Lists.newArrayList(joined
        .materialize());
    Collections.sort(joinedResultList);

    assertEquals(expectedJoinResult, joinedResultList);
  }

  private static PTable<Integer, String> readTable(Pipeline pipeline, String filename) {
    try {
      return pipeline.readTextFile(FileHelper.createTempCopyOf(filename)).parallelDo("asTable",
          new LineSplitter(), Writables.tableOf(Writables.ints(), Writables.strings()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
