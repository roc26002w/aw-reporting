// Copyright 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.api.ads.adwords.awreporting.exporter.reportwriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.api.ads.common.lib.exception.OAuthException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Maps;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

/**
 * Provides an authenticated Google {@link Drive} service instance configured for AW Reports to DB.
 *   
 * @author joeltoby@google.com (Joel Toby)
 * @author jtoledo@google.com (Julian Toledo)
 *
 */
public class GoogleDriveService {

  private static final Logger LOGGER = Logger.getLogger(GoogleDriveService.class);

  private static final HashMap<Credential, GoogleDriveService> googleDriveServiceHash = Maps.newHashMap();
  
  private static final String DRIVE_APP_NAME = "AwReporting-AppEngine";

  private static final String REPORT_FOLDER_NAME_PRE = "AW Reports - AdWords generated Reports";
  
  private static final String ACCOUNT_SUB_FOLDER_NAME_PRE = "Account ID#";

  private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

  private Drive service;

  /**
   * Constructs the GoogleDriveService for the Authenticator
   * It stores GoogleDriveService in a hash for re-usability.
   */
  private GoogleDriveService(Credential credential) throws OAuthException {
    this.service =  new Drive.Builder(new NetHttpTransport(), new JacksonFactory(),
        credential).setApplicationName(DRIVE_APP_NAME).build();    
    googleDriveServiceHash.put(credential, this);
  }

  /**
   * Gets a single GoogleDriveService instance per Authenticator
   */
  public static GoogleDriveService getGoogleDriveService(Credential credential) throws OAuthException {
    GoogleDriveService googleDriveService = googleDriveServiceHash.get(credential);
    if (googleDriveService == null) {
      synchronized (GoogleDriveService.class) {
        if (googleDriveService == null) {
          googleDriveService = new GoogleDriveService(credential);
        }
      }
    }
    return googleDriveService;
  }

  /**
   * Gets the Drive instance for the GoogleDriveService
   */
  public Drive getDriveService() {
    return service;
  }

  /**
   * Gets the AW Reports Google Drive folder. If one does not exist, it will be created.
   * @param mccAccountId
   * @throws IOException
   */
  public synchronized File getReportsFolder(String mccAccountId) throws IOException {
    String reportFolderName = REPORT_FOLDER_NAME_PRE + ": " + mccAccountId;

    // Check if the folder exists
    List<File> results = new ArrayList<File>();
    LOGGER.info("Building find folder query");
    Files.List request = service.files().list()
        .setQ("title= '" + reportFolderName + "' and mimeType='" 
         + FOLDER_MIME_TYPE +"' and trashed = false");
    LOGGER.info("Executing find folder query");
    FileList files = request.execute();
    LOGGER.info("Number of results from query: " + files.size());
    results.addAll(files.getItems());

    if(!results.isEmpty()) {
      // Found the existing folder
      return results.get(0);

    } else {
      // Folder does not exist. Create it.
      LOGGER.info("Creating folder");
      File reportsFolder = new File();
      reportsFolder.setTitle(reportFolderName);
      reportsFolder.setMimeType(FOLDER_MIME_TYPE);
      reportsFolder.setDescription("Contains AdWords Reports generated by AwReporting");

      LOGGER.info("Executing create folder");
      return service.files().insert(reportsFolder).execute();
    }
  }
  
  /**
   * Gets the AW Reports Google Drive sub-folder for a specific account. If one does not exist, it will be created.
   * @param mccDriveDirectory the top directory for the MCC into which sub-folders will be added.
   * @param accountId
   * @throws IOException
   */
  public synchronized File getAccountFolder(File mccDriveDirectory, String accountId) throws IOException {
    LOGGER.info("*** getAccountFolder");
    String accountFolderName = ACCOUNT_SUB_FOLDER_NAME_PRE + ": " + accountId;

    // Check if the sub-folder exists within main folder only
    List<ChildReference> results = new ArrayList<ChildReference>();
    LOGGER.info("Building find sub-folder query");
    com.google.api.services.drive.Drive.Children.List request = service.children().list(mccDriveDirectory.getId())
        .setQ("title= '" + accountFolderName + "' and mimeType='" 
         + FOLDER_MIME_TYPE +"' and trashed = false");
    LOGGER.info("Executing find folder query");
    ChildList files = request.execute();
    LOGGER.info("Number of results from query: " + files.size());
    results.addAll(files.getItems());

    if(!results.isEmpty()) {
      return new File().setId(results.get(0).getId());

    } else {
      // Folder does not exist. Create it.
      LOGGER.info("Creating sub-folder");
      File accountFolder = new File();
      accountFolder.setTitle(accountFolderName);
      accountFolder.setMimeType(FOLDER_MIME_TYPE);
      accountFolder.setDescription("AdWords Reports generated by AwReporting for account# " + accountId);
      accountFolder.setParents(Arrays.asList(new ParentReference().setId(mccDriveDirectory.getId())));

      LOGGER.info("Executing create folder");
      return service.files().insert(accountFolder).execute();
    }
  }
  
  /**
   * Gets a Google Drive file by ID or null if it does not exist.
   * Currently used as a workaround for Drive bug (https://code.google.com/p/google-apps-script-issues/issues/detail?id=3713)
   * @param mccAccountId
   * @throws IOException
   */
  public synchronized File getFileById(String fileId) throws IOException {
    File file = service.files().get(fileId).execute();
    return file;
  }
}