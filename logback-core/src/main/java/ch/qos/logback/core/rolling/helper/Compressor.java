/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2011, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.WarnStatus;

/**
 * The <code>Compression</code> class implements ZIP and GZ file
 * compression/decompression methods.
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public class Compressor extends ContextAwareBase {

  final CompressionMode compressionMode;

  public Compressor(CompressionMode compressionMode) {
    this.compressionMode = compressionMode;
  }

    /**
     *
     * @param nameOfFile2Compress
     * @param nameOfCompressedFile
     * @param innerEntryName The name of the file within the zip file. Use for ZIP compression.
     */
  public void compress(String nameOfFile2Compress, String nameOfCompressedFile, String innerEntryName) {
    switch (compressionMode) {
    case GZ:
      addInfo("GZ compressing [" + nameOfFile2Compress + "].");
      gzCompress(nameOfFile2Compress, nameOfCompressedFile);
      break;
    case ZIP:
      addInfo("ZIP compressing [" + nameOfFile2Compress + "].");
      zipCompress(nameOfFile2Compress, nameOfCompressedFile, innerEntryName);
      break;
    case NONE:
      throw new UnsupportedOperationException(
          "compress method called in NONE compression mode");
    }
  }

  private void zipCompress(String nameOfFile2zip, String nameOfZippedFile, String innerEntryName) {
    File file2zip = new File(nameOfFile2zip);

    if (!file2zip.exists()) {
      addStatus(new WarnStatus("The file to compress named [" + nameOfFile2zip
          + "] does not exist.", this));

      return;
    }

    if(innerEntryName == null) {
      addStatus(new WarnStatus("The innerEntryName parameter cannot be null", this));
      return;
    }

    if (!nameOfZippedFile.endsWith(".zip")) {
      nameOfZippedFile = nameOfZippedFile + ".zip";
    }

    File zippedFile = new File(nameOfZippedFile);

    if (zippedFile.exists()) {
      addStatus(new WarnStatus("The target compressed file named ["
          + nameOfZippedFile + "] exist already.", this));

      return;
    }

    try {
      FileOutputStream fos = new FileOutputStream(nameOfZippedFile);
      ZipOutputStream zos = new ZipOutputStream(fos);
      FileInputStream fis = new FileInputStream(nameOfFile2zip);

      ZipEntry zipEntry = computeZipEntry(innerEntryName);
      zos.putNextEntry(zipEntry);

      byte[] inbuf = new byte[8102];
      int n;

      while ((n = fis.read(inbuf)) != -1) {
        zos.write(inbuf, 0, n);
      }

      fis.close();
      zos.close();

      if (!file2zip.delete()) {
        addStatus(new WarnStatus("Could not delete [" + nameOfFile2zip + "].",
            this));
      }
    } catch (Exception e) {
      addStatus(new ErrorStatus("Error occurred while compressing ["
          + nameOfFile2zip + "] into [" + nameOfZippedFile + "].", this, e));
    }
  }

  // http://jira.qos.ch/browse/LBCORE-98
  // The name of the compressed file as nested within the zip archive
  //
  // Case 1: RawFile = null, Patern = foo-%d.zip
  // nestedFilename = foo-${current-date}
  //
  // Case 2: RawFile = hello.txt, Pattern = = foo-%d.zip
  // nestedFilename = foo-${current-date}
  //
  // in both cases, the strategy consisting of removing the compression
  // suffix of zip file works reasonably well. The alternative strategy
  // whereby the nested file name was based on the value of the raw file name
  // (applicable to case 2 only) has the disadvantage of the nested files
  // all having the same name, which could make it harder for the user
  // to unzip the file without collisions
  ZipEntry computeZipEntry(File zippedFile) {
    return computeZipEntry(zippedFile.getName());
  }

  ZipEntry computeZipEntry(String filename) {
    String nameOfFileNestedWithinArchive = computeFileNameStr_WCS(filename, compressionMode);
    return new ZipEntry(nameOfFileNestedWithinArchive);
  }


  private void gzCompress(String nameOfFile2gz, String nameOfgzedFile) {
    File file2gz = new File(nameOfFile2gz);

    if (!file2gz.exists()) {
      addStatus(new WarnStatus("The file to compress named [" + nameOfFile2gz
          + "] does not exist.", this));

      return;
    }

    if (!nameOfgzedFile.endsWith(".gz")) {
      nameOfgzedFile = nameOfgzedFile + ".gz";
    }

    File gzedFile = new File(nameOfgzedFile);

    if (gzedFile.exists()) {
      addStatus(new WarnStatus("The target compressed file named ["
          + nameOfgzedFile + "] exist already.", this));

      return;
    }

    try {
      FileOutputStream fos = new FileOutputStream(nameOfgzedFile);
      GZIPOutputStream gzos = new GZIPOutputStream(fos);
      FileInputStream fis = new FileInputStream(nameOfFile2gz);
      byte[] inbuf = new byte[8102];
      int n;

      while ((n = fis.read(inbuf)) != -1) {
        gzos.write(inbuf, 0, n);
      }

      fis.close();
      gzos.close();

      if (!file2gz.delete()) {
        addStatus(new WarnStatus("Could not delete [" + nameOfFile2gz + "].",
            this));
      }
    } catch (Exception e) {
      addStatus(new ErrorStatus("Error occurred while compressing ["
          + nameOfFile2gz + "] into [" + nameOfgzedFile + "].", this, e));
    }
  }

  static public String computeFileNameStr_WCS(String fileNamePatternStr,
                                              CompressionMode compressionMode) {
    int len = fileNamePatternStr.length();
    switch (compressionMode) {
      case GZ:
    	  if(fileNamePatternStr.endsWith(".gz")) 
            return fileNamePatternStr.substring(0, len - 3);
    	  else 
    		 return fileNamePatternStr;
      case ZIP:
    	  if(fileNamePatternStr.endsWith(".zip")) 
    	  return fileNamePatternStr.substring(0, len - 4);
    	  else 
    		return fileNamePatternStr;	  
      case NONE:
        return fileNamePatternStr;
    }
    throw new IllegalStateException("Execution should not reach this point");
  }

  @Override
  public String toString() {
    return "c.q.l.core.rolling.helper.Compress";
  }

}
