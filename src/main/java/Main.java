import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        final Options options = new Options();
        options.addOption("list", true, "list files in this folder");
        options.addOption("upload", false, "upload a file to given folder");
        options.addOption("upload_data", false, "upload a data file to the default folder");
        options.addOption("i", true, "path to the upload file");
        options.addOption("type", true, "type of the upload file");
        options.addOption("name", true, "name in Google drive");
        options.addOption("parent", true, "parent folder id");
        options.addOption("help", false, "print help message");
        options.addOption("create_folder", false, "create a folder in Google drive");

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cml = parser.parse(options, args);

            // parse help
            if (cml.hasOption("help")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp("java -jar drive_service.jar ", options);
                System.exit(0);
            }

            // parse list
            if (cml.hasOption("list")) {
                if (cml.getOptionValue("list").equals("default")) {
                    DriveUtils.listCurrentDataFiles(DriveUtils.getDefaultFolderId());
                }
                else {
                    DriveUtils.listCurrentDataFiles(cml.getOptionValue("list"));
                }
                System.exit(0);
            }

            // parse upload
            if (cml.hasOption("upload")) {
                if (cml.hasOption("i") && cml.hasOption("name") && cml.hasOption("type") && cml.hasOption("parent")) {
                    DriveUtils.uploadFile(cml.getOptionValue("i"), cml.getOptionValue("name"),
                            cml.getOptionValue("type"), cml.getOptionValue("parent"));
                }
                else {
                    throw new ParseException("Parameters are not enough. Required 'i', 'name', 'type' and 'parent'.");
                }
                System.exit(0);
            }

            // parse upload_data
            if (cml.hasOption("upload_data")) {
                if (cml.hasOption("i")) {
                    DriveUtils.uploadDataFile(cml.getOptionValue("i"));
                }
                else {
                    throw new ParseException("Parameters are not enough. Required 'i'.");
                }
                System.exit(0);
            }
        }
        catch (ParseException | IOException | GeneralSecurityException e) {
            logger.error(e.getMessage());
        }
    }
}