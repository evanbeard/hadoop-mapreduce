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

package org.apache.hadoop.streaming;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.mapreduce.server.jobtracker.JTConfig;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.util.StringUtils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This class tests cacheArchive option of streaming 
 * The test case creates 2 archive files, ships it with hadoop
 * streaming and compares the output with expected output
 */
public class TestMultipleArchiveFiles extends TestStreaming
{
  private static final Log LOG = LogFactory.getLog(TestMultipleArchiveFiles.class);

  private StreamJob job;
  private String INPUT_DIR = "multiple-archive-files/";
  private String INPUT_FILE = INPUT_DIR + "input.txt";
  private String CACHE_ARCHIVE_1 = INPUT_DIR + "cacheArchive1.zip";
  private File CACHE_FILE_1 = null;
  private String CACHE_ARCHIVE_2 = INPUT_DIR + "cacheArchive2.zip";
  private File CACHE_FILE_2 = null;
  private String expectedOutput = null;
  private String OUTPUT_DIR = "out";
  private Configuration conf = null;
  private MiniDFSCluster dfs = null;
  private MiniMRCluster mr = null;
  private FileSystem fileSys = null;
  private String strJobTracker = null;
  private String strNamenode = null;
  private String namenode = null;

  public TestMultipleArchiveFiles() throws Exception {
    CACHE_FILE_1 = new File("cacheArchive1");
    CACHE_FILE_2 = new File("cacheArchive2");
    input = "HADOOP";
    expectedOutput = "HADOOP\t\nHADOOP\t\n";
    conf = new Configuration();      
    dfs = new MiniDFSCluster(conf, 1, true, null);      
    fileSys = dfs.getFileSystem();
    namenode = fileSys.getUri().getAuthority();
    mr  = new MiniMRCluster(1, namenode, 3);
    strJobTracker = JTConfig.JT_IPC_ADDRESS + "=localhost:" + mr.getJobTrackerPort();
    strNamenode = "fs.default.name=" + namenode;

    map = "xargs cat";
    reduce = "cat";
  }

  @Override
  protected void setInputOutput() {
    inputFile = INPUT_FILE;
    outDir = OUTPUT_DIR;
  }
  
  protected void createInput() throws IOException
  {
    fileSys.delete(new Path(INPUT_DIR), true);
    DataOutputStream dos = fileSys.create(new Path(INPUT_FILE));
    String inputFileString = "symlink1/cacheArchive1\nsymlink2/cacheArchive2";
    dos.write(inputFileString.getBytes("UTF-8"));
    dos.close();
    
    DataOutputStream out = fileSys.create(new Path(CACHE_ARCHIVE_1.toString()));
    ZipOutputStream zos = new ZipOutputStream(out);
    ZipEntry ze = new ZipEntry(CACHE_FILE_1.toString());
    zos.putNextEntry(ze);
    zos.write(input.getBytes("UTF-8"));
    zos.closeEntry();
    zos.close();

    out = fileSys.create(new Path(CACHE_ARCHIVE_2.toString()));
    zos = new ZipOutputStream(out);
    ze = new ZipEntry(CACHE_FILE_2.toString());
    zos.putNextEntry(ze);
    zos.write(input.getBytes("UTF-8"));
    zos.closeEntry();
    zos.close();
  }

  protected String[] genArgs() {
    String workDir = fileSys.getWorkingDirectory().toString() + "/";
    String cache1 = workDir + CACHE_ARCHIVE_1 + "#symlink1";
    String cache2 = workDir + CACHE_ARCHIVE_2 + "#symlink2";

    args.add("-jobconf");
    args.add("mapreduce.job.reduces=1");
    args.add("-cacheArchive");
    args.add(cache1);
    args.add("-cacheArchive");
    args.add(cache2);
    args.add("-jobconf");
    args.add(strNamenode);
    args.add("-jobconf");
    args.add(strJobTracker);
    return super.genArgs();
  }

  protected void checkOutput() throws IOException {
    StringBuffer output = new StringBuffer(256);
    Path[] fileList = FileUtil.stat2Paths(fileSys.listStatus(
                                            new Path(OUTPUT_DIR)));
    for (int i = 0; i < fileList.length; i++){
      LOG.info("Adding output from file: " + fileList[i]);
      output.append(StreamUtil.slurpHadoop(fileList[i], fileSys));
    }
    assertEquals(expectedOutput, output.toString());
  }
}
