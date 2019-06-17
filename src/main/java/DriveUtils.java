import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import io.bretty.console.table.Alignment;
import io.bretty.console.table.ColumnFormatter;
import io.bretty.console.table.Table;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.*;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Properties;


/**
 * Utility class of Google drive services.
 */
public class DriveUtils {
    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final Properties prop = new Properties();
    static {
        InputStream in = Main.class.getResourceAsStream("/config.properties");
        try {
            if (in == null) {
                throw new FileNotFoundException("config.properties not found.");
            }
            prop.load(in);
        }
        catch (IOException e) {
            logger.error(e.getMessage());
            logger.error("Fail to load config file.");
            System.exit(-1);
        }
    }

    private static final String APPLICATION_NAME = prop.getProperty("APPLICATION_NAME");
    private static final String TOKENS_DIRECTORY_PATH = prop.getProperty("TOKENS_DIRECTORY_PATH");

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     * Current scope is allowing this program see, edit, create, and delete all of your Google Drive files.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = prop.getProperty("CREDENTIALS_FILE_PATH");

    /**
     * This is the default folder used to upload files.
     * Current default folder is: https://drive.google.com/drive/u/1/folders/1U0J1WIELWtPr74153hlYyNCI49YThqbt
     * Can be changed in config file.
     */
    private static final String DEFAULT_FOLDER_ID = prop.getProperty("DEFAULT_FOLDER_ID");

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private DriveUtils() { }

    /**
     * Creates an authorized Credential object. You may need to login your Google account for the first time.
     * See https://developers.google.com/drive/api/v3/about-sdk for details.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Get an instance of Google Drive service.
     *
     * @return Google Drive service instance.
     */
    private static Drive getDriveService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Upload a given file to Google drive.
     *
     * @param pathName Path to the file need to be uploaded.
     * @param nameInDrive Name want to use in google drive.
     * @param type Type of this file. Refer to this: https://developers.google.com/drive/api/v3/mime-types
     * @param parentFolderId ID of this file's parent folder.
     */
    static void uploadFile(String pathName, String nameInDrive, String type, String parentFolderId)
            throws IOException, GeneralSecurityException {
        Drive service = getDriveService();

        File fileMeta = new File();
        fileMeta.setName(nameInDrive);
        fileMeta.setParents(Collections.singletonList(parentFolderId));

        FileContent mediaContent = new FileContent(type, new java.io.File(pathName));

        logger.info(String.format("Upload file size: %.2f MBytes.", (double) mediaContent.getLength() / (1024 * 1024)));

        File file = service.files().create(fileMeta, mediaContent)
                .setFields("id, parents")
                .execute();

        logger.info("Success. File link: https://drive.google.com/open?id=" + file.getId());
    }

    /**
     * Create a new folder in Google drive.
     *
     * @param folderName Folder name want to create.
     * @param parentFolderId ID of this folder's parent folder.
     */
    static void createFolder(String folderName, String parentFolderId) throws IOException, GeneralSecurityException {
        Drive service = getDriveService();

        File fileMeta = new File();
        fileMeta.setName(folderName);
        fileMeta.setMimeType("application/vnd.google-apps.folder");
        fileMeta.setParents(Collections.singletonList(parentFolderId));

        File file = service.files().create(fileMeta)
                .setFields("id, parents")
                .execute();

        logger.info("Success. Folder link: https://drive.google.com/open?id=" + file.getId());
    }

    /**
     * List all files under a given folder and display by name.
     *
     * @param folderId ID of the folder want to see.
     */
    static void listCurrentDataFiles(String folderId) throws IOException, GeneralSecurityException {
        Drive service = getDriveService();

        FileList result = service.files().list()
                .setQ(String.format("'%s' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false", folderId))
                .setFields("nextPageToken, files(id, name, createdTime, mimeType)")
                .execute();

        List<File> files = result.getFiles();

        if (files == null || files.isEmpty()) {
            logger.info("No files found");
        }
        else {
            logger.info(String.format("Found %d files under folder: %s.", files.size(), folderId));

            String[] fnames = new String[files.size()];
            String[] times = new String[files.size()];
            String[] types = new String[files.size()];
            String[] links = new String[files.size()];

            String url = "https://drive.google.com/open?id=";

            int maxLengthOfFName = 0;
            int maxLengthOfTime = 0;
            int maxLengthOfType = 0;
            int maxLengthOfLink = 0;

            for (int i = 0; i < files.size(); ++ i) {
                fnames[i] = files.get(i).getName();
                times[i] = files.get(i).getCreatedTime().toString();
                types[i] = files.get(i).getMimeType();
                links[i] = url + files.get(i).getId();

                maxLengthOfFName = maxLengthOfFName > fnames[i].length() ? maxLengthOfFName : fnames[i].length();
                maxLengthOfTime = maxLengthOfTime > times[i].length() ? maxLengthOfTime : times[i].length();
                maxLengthOfType = maxLengthOfType > types[i].length() ? maxLengthOfType : types[i].length();
                maxLengthOfLink = maxLengthOfLink > links[i].length() ? maxLengthOfLink : links[i].length();
            }

            ColumnFormatter<String> fnameFormatter = ColumnFormatter.text(Alignment.CENTER, maxLengthOfFName + 4);
            ColumnFormatter<String> timeFormatter = ColumnFormatter.text(Alignment.CENTER, maxLengthOfTime + 4);
            ColumnFormatter<String> typeFormatter = ColumnFormatter.text(Alignment.CENTER, maxLengthOfType + 4);
            ColumnFormatter<String> linkFormatter = ColumnFormatter.text(Alignment.CENTER, maxLengthOfLink + 4);

            Table.Builder builder = new Table.Builder("file name", fnames, fnameFormatter);
            builder.addColumn("create time", times, timeFormatter);
            builder.addColumn("type", types, typeFormatter);
            builder.addColumn("link", links, linkFormatter);

            Table table = builder.build();

            System.out.println(table);
        }
    }

    /**
     * Helper function to upload generated data file (csv format) to our team drive.
     * The default file name is data_{datetime}.csv
     * The default upload folder is stored in config.properties
     *
     * @param dataPath Path to the data file.
     */
    static void uploadDataFile(String dataPath) throws IOException, GeneralSecurityException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_hhmmss");
        LocalDateTime now = LocalDateTime.now();
        String fileNmae = String.format("data_%s.csv", formatter.format(now));

        uploadFile(dataPath, fileNmae, "text/csv", DEFAULT_FOLDER_ID);
    }

    static String getDefaultFolderId() {
        return DEFAULT_FOLDER_ID;
    }

    static String getApplicationName() {
        return APPLICATION_NAME;
    }

    static String getCredentialsFilePath() {
        return CREDENTIALS_FILE_PATH;
    }

    static String getTokensDirectoryPath() {
        return TOKENS_DIRECTORY_PATH;
    }
}
